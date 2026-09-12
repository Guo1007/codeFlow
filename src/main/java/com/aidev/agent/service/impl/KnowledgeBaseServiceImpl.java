package com.aidev.agent.service.impl;

import com.aidev.agent.common.ServiceException;
import com.aidev.agent.controller.vo.KnowledgeDocVO;
import com.aidev.agent.controller.vo.KnowledgeHitVO;
import com.aidev.agent.dal.entity.KbDocument;
import com.aidev.agent.dal.mapper.KbDocumentMapper;
import com.aidev.agent.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务实现类：文档切分 → embedding → 存入 Redis 向量库（RediSearch），并提供检索。
 * <p>
 * 向量存储 {@link EmbeddingStore} 与检索所需的 Redis 连接均由
 * {@link com.aidev.agent.config.AiConfig}（langchain4j-community-redis starter 自动装配）统一注册，
 * 业务层只注入使用，不再关心 Jedis 连接与维度探测。文档元数据与原文存 MySQL（kb_document），向量段存 Redis。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final String DOC_TYPE_UPLOAD = "upload";
    private static final String DOC_TYPE_DESIGN = "design";
    /** 切分片段的最大字符数 */
    private static final int CHUNK_SIZE = 800;

    private final KbDocumentMapper documentMapper;

    /**
     * Redis 向量库（AiConfig 注册）
     */
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel;

    /**
     * 文档解析器（AiConfig 注册，Apache Tika）
     */
    private final DocumentParser documentParser;

    @Override
    public List<KnowledgeDocVO> listDocuments(String userId, String project) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(userId != null && !userId.isBlank(), KbDocument::getCreator, userId)
                .eq(project != null && !project.isBlank(), KbDocument::getProject, project)
                .orderByDesc(KbDocument::getCreateTime);
        return documentMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public KnowledgeDocVO uploadDocument(String userId, String project, String name, String content) {
        if (content == null || content.isBlank()) {
            throw new ServiceException("文档内容不能为空");
        }
        String docName = (name == null || name.isBlank()) ? "未命名文档" : name.trim();
        String documentId = UUID.randomUUID().toString().replace("-", "");
        KbDocument doc = new KbDocument();
        doc.setDocumentId(documentId);
        doc.setProject(project == null ? "" : project);
        doc.setDocType(DOC_TYPE_UPLOAD);
        doc.setVersion(1);
        doc.setName(docName);
        doc.setContent(content);
        doc.setEmbeddingStatus(0);
        doc.setCreator(userId);
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        doc.setDeleted(false);
        documentMapper.insert(doc);
        // 向量化（处理中状态入库后再计算，异常时回滚为失败由调用方感知）
        embedAndStore(documentId, project == null ? "" : project, docName, 1, content);
        doc.setEmbeddingStatus(1);
        documentMapper.updateById(doc);
        log.info("[uploadDocument][{}] 上传并向量化文档「{}」", userId, docName);
        return toVO(doc);
    }

    @Override
    public KnowledgeDocVO uploadDocumentFile(String userId, String project, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("文件内容为空");
        }
        String text;
        try {
            Document doc = documentParser.parse(new ByteArrayInputStream(bytes));
            text = doc.text();
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            log.warn("[uploadDocumentFile][{}] 文件解析失败：{}", fileName, e.getMessage());
            throw new ServiceException("无法解析文件「" + fileName + "」，请确认格式受支持");
        }
        if (text == null || text.isBlank()) {
            throw new ServiceException("未从「" + fileName + "」中解析出文本内容，可能为图片/扫描件或不受支持的格式");
        }
        String name = (fileName == null || fileName.isBlank()) ? "上传文档" : fileName;
        return uploadDocument(userId, project, name, text);
    }

    @Override
    public void deleteDocument(Long id, String userId) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new ServiceException("文档不存在");
        }
        if (!String.valueOf(doc.getCreator()).equals(userId)) {
            throw new ServiceException("无权删除该文档");
        }
        removeSegmentVectors(doc);
        documentMapper.deleteById(id);
    }

    @Override
    public void indexDesign(String projectName, String content, Integer version, String creator) {
        if (content == null || content.isBlank()) {
            return;
        }
        String project = projectName == null ? "" : projectName;
        // 幂等：该文档同一版本已存在且已完成向量化则跳过
        List<KbDocument> existing = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getProject, project)
                .eq(KbDocument::getDocType, DOC_TYPE_DESIGN)
                .eq(KbDocument::getVersion, version));
        if (existing.stream().anyMatch(d -> Integer.valueOf(1).equals(d.getEmbeddingStatus()))) {
            return;
        }
        // 替换：删除该项目 design 类型的旧版本文档（向量 + 记录），避免重复存储
        removeDesignOld(project);
        String documentId = project + "_design_v" + version;
        String docName = "设计文档 v" + version;
        KbDocument doc = new KbDocument();
        doc.setDocumentId(documentId);
        doc.setProject(project);
        doc.setDocType(DOC_TYPE_DESIGN);
        doc.setVersion(version == null ? 1 : version);
        doc.setName(docName);
        doc.setContent(content);
        doc.setEmbeddingStatus(0);
        doc.setCreator(creator == null ? "" : creator);
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        doc.setDeleted(false);
        documentMapper.insert(doc);
        embedAndStore(documentId, project, docName, doc.getVersion(), content);
        doc.setEmbeddingStatus(1);
        documentMapper.updateById(doc);
        log.info("[indexDesign][{}] 设计文档 v{} 已向量化入库", project, doc.getVersion());
    }

    @Override
    public List<KnowledgeHitVO> search(String project, String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            // 多取一些再按项目过滤
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(Math.max(topK * 4, 16))
                    .build();
            List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> matches =
                    embeddingStore.search(searchRequest).matches();
            String scope = project == null ? "" : project;
            List<KnowledgeHitVO> hits = new ArrayList<>();
            for (dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment> match : matches) {
                if (hits.size() >= topK) {
                    break;
                }
                TextSegment segment = match.embedded();
                if (segment == null) {
                    continue;
                }
                Metadata metadata = segment.metadata();
                String segProject = metadata.getString("project");
                if (segProject == null) {
                    segProject = "";
                }
                // 只返回当前项目 + 全局的片段
                if (!segProject.equals(scope) && !segProject.isEmpty()) {
                    continue;
                }
                KnowledgeHitVO vo = new KnowledgeHitVO();
                String hitName = metadata.getString("name");
                vo.setName(hitName == null ? "" : hitName);
                vo.setProject(segProject);
                vo.setContent(segment.text());
                hits.add(vo);
            }
            return hits;
        } catch (Exception e) {
            log.warn("[search][知识库检索失败] {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 删除该项目 design 类型所有旧版本文档（向量 + 记录），供定稿替换使用
     */
    private void removeDesignOld(String project) {
        List<KbDocument> olds = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getProject, project)
                .eq(KbDocument::getDocType, DOC_TYPE_DESIGN));
        for (KbDocument old : olds) {
            removeSegmentVectors(old);
            documentMapper.deleteById(old.getId());
        }
    }

    /**
     * 按文档记录的 segmentIds 物理删除 Redis 向量段
     */
    private void removeSegmentVectors(KbDocument doc) {
        if (doc.getSegmentIds() == null || doc.getSegmentIds().isBlank()) {
            return;
        }
        try {
            embeddingStore.removeAll(Arrays.stream(doc.getSegmentIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList());
        } catch (Exception e) {
            log.warn("[removeSegmentVectors][清理向量段失败 name={}] {}", doc.getName(), e.getMessage());
        }
    }

    /**
     * 切分文本 → 逐段 embedding → 写入 Redis 向量库，并把段 ID 回写文档记录
     */
    private void embedAndStore(String documentId, String project, String name, int version, String content) {
        List<String> chunks = chunkText(content);
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            Metadata metadata = Metadata.from(Map.of(
                    "documentId", documentId,
                    "project", project == null ? "" : project,
                    "name", name,
                    "version", version));
            segments.add(TextSegment.from(chunk, metadata));
        }
        // 逐段 embedding，再由存储批量写入并返回各段 id（EmbeddingStore 接口默认自动生成 id）
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            embeddings.add(embeddingModel.embed(segment).content());
        }
        List<String> ids = embeddingStore.addAll(embeddings, segments);
        KbDocument update = new KbDocument();
        update.setDocumentId(documentId);
        update.setSegmentIds(String.join(",", ids));
        documentMapper.update(update, new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getDocumentId, documentId));
    }

    /**
     * 简单文本切分：优先按段落（连续空行）整段切，超过 CHUNK_SIZE 再按字符切
     */
    private static List<String> chunkText(String content) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();
        for (String p : paragraphs) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (buf.length() + t.length() + 1 > CHUNK_SIZE && buf.length() > 0) {
                result.add(buf.toString().trim());
                buf.setLength(0);
            }
            buf.append(t).append('\n');
        }
        if (buf.length() > 0) {
            result.add(buf.toString().trim());
        }
        // 极端：仍有超长段时按字符硬切
        List<String> finalResult = new ArrayList<>();
        for (String s : result) {
            if (s.length() <= CHUNK_SIZE * 2) {
                finalResult.add(s);
            } else {
                for (int i = 0; i < s.length(); i += CHUNK_SIZE) {
                    finalResult.add(s.substring(i, Math.min(s.length(), i + CHUNK_SIZE)));
                }
            }
        }
        return finalResult.isEmpty() ? List.of(content) : finalResult;
    }

    private KnowledgeDocVO toVO(KbDocument d) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(d.getId());
        vo.setDocumentId(d.getDocumentId());
        vo.setProject(d.getProject());
        vo.setDocType(d.getDocType());
        vo.setVersion(d.getVersion());
        vo.setName(d.getName());
        vo.setEmbeddingStatus(d.getEmbeddingStatus());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

}
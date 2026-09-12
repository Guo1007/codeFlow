package com.aidev.agent.controller;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.common.UserContext;
import com.aidev.agent.controller.vo.KnowledgeDocVO;
import com.aidev.agent.controller.vo.KnowledgeHitVO;
import com.aidev.agent.controller.vo.KnowledgeUploadReqVO;
import com.aidev.agent.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识库控制器（文档上传/列表/删除/检索），按登录用户隔离。
 */
@Validated
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 文档列表（当前用户的 upload 文档 + 其定稿设计文档）
     */
    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocVO>> list(@RequestParam(value = "project", required = false) String project) {
        return ApiResponse.success(knowledgeBaseService.listDocuments(UserContext.getUserIdStr(), project));
    }

    /**
     * 上传文档并向量化
     */
    @PostMapping("/document/upload")
    public ApiResponse<KnowledgeDocVO> upload(@Valid @RequestBody KnowledgeUploadReqVO reqVO) {
        return ApiResponse.success(knowledgeBaseService.uploadDocument(
                UserContext.getUserIdStr(), reqVO.getProject(), reqVO.getName(), reqVO.getContent()));
    }

    /**
     * 上传文件并自动解析后向量化（PDF/Word/Excel/PPT/HTML/TXT 等）
     */
    @PostMapping("/document/upload-file")
    public ApiResponse<KnowledgeDocVO> uploadFile(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "project", required = false) String project)
            throws IOException {
        return ApiResponse.success(knowledgeBaseService.uploadDocumentFile(
                UserContext.getUserIdStr(), project, file.getOriginalFilename(), file.getBytes()));
    }

    /**
     * 删除文档（物理删除记录 + Redis 向量段）
     */
    @DeleteMapping("/document")
    public ApiResponse<Boolean> delete(@RequestParam("id") Long id) {
        knowledgeBaseService.deleteDocument(id, UserContext.getUserIdStr());
        return ApiResponse.success(true);
    }

    /**
     * 向量检索（对话 RAG 与调试用）
     */
    @GetMapping("/search")
    public ApiResponse<List<KnowledgeHitVO>> search(@RequestParam(value = "project", required = false) String project,
                                                    @RequestParam("q") String q,
                                                    @RequestParam(value = "topK", defaultValue = "3") int topK) {
        return ApiResponse.success(knowledgeBaseService.search(project, q, topK));
    }

}
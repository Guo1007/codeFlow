package com.aidev.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 项目代码阅读工具（自 ruoyi-vue-pro 原型阶段迁移）。
 * <p>
 * 每个目标项目一个实例（由 ProjectChatServiceFactory 按配置构建），
 * 工作范围锁定在该实例的项目根目录内，不允许越界访问。
 * 为 LangChain4j Agent 提供标注 {@code @Tool} 的方法，使 AI 能基于真实代码回答问题。
 * </p>
 */
@Slf4j
public class ProjectCodeTools {

    /**
     * 读取文件的最大行数，防止大文件撑爆上下文
     */
    private static final int MAX_FILE_LINES = 400;

    /**
     * 搜索结果的最大匹配条数
     */
    private static final int MAX_SEARCH_RESULTS = 50;

    /**
     * 目录结构展示的最大深度
     */
    private static final int MAX_TREE_DEPTH = 3;

    /**
     * 目录结构输出的最大条目数
     */
    private static final int MAX_TREE_ENTRIES = 200;

    /**
     * 参与代码搜索的文件扩展名（跳过二进制与构建产物）
     */
    private static final Set<String> SEARCHABLE_EXTENSIONS = Set.of(
            "java", "xml", "yaml", "yml", "properties", "sql", "ts", "vue", "js", "json", "md", "txt");

    /**
     * 遍历时跳过的目录（构建产物、依赖、版本库等）
     */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            "target", "node_modules", ".git", ".idea", "dist", "logs");

    /**
     * 本实例绑定的目标项目根目录
     */
    private final Path root;

    public ProjectCodeTools(String rootPath) {
        this.root = Paths.get(rootPath);
    }

    /**
     * 列出项目的目录结构（树形）。
     * <p>
     * 展示指定子目录（默认根目录）下最多 3 层的目录与文件名，跳过 target、node_modules 等构建产物。
     * </p>
     *
     * @param subPath 子目录路径，相对项目根目录，传空字符串或 "." 表示根目录
     * @return 树形目录结构文本
     */
    @Tool("列出项目的目录结构（树形，最多3层，跳过target和node_modules等构建产物），用于了解项目整体布局。subPath传空字符串表示项目根目录")
    public String listProjectStructure(String subPath) {
        Path dir = resolve(subPath);
        if (dir == null) {
            return "路径无效：" + subPath;
        }
        if (!Files.isDirectory(dir)) {
            return "不是目录：" + subPath;
        }
        StringBuilder sb = new StringBuilder("【项目结构】").append(subPath == null ? "" : subPath).append("\n");
        try (Stream<Path> stream = Files.walk(dir, MAX_TREE_DEPTH)) {
            List<Path> paths = stream
                    .filter(this::isNotSkipped)
                    .limit(MAX_TREE_ENTRIES + 1)
                    .collect(Collectors.toList());
            for (Path path : paths) {
                if (path.equals(dir)) {
                    continue;
                }
                int depth = dir.relativize(path).getNameCount();
                sb.append("  ".repeat(Math.max(0, depth - 1)))
                        .append(Files.isDirectory(path) ? "[目录] " : "      ")
                        .append(path.getFileName())
                        .append("\n");
            }
            if (paths.size() > MAX_TREE_ENTRIES) {
                sb.append("...（条目过多已截断，可指定更具体的 subPath 查看子目录）\n");
            }
        } catch (IOException e) {
            log.error("[listProjectStructure][读取目录失败 {}]", subPath, e);
            return "读取目录失败：" + e.getMessage();
        }
        return sb.toString();
    }

    /**
     * 读取项目中的指定源码文件内容。
     *
     * @param relativePath 文件相对路径，如 yudao-module-ai-lc4j/pom.xml
     * @return 文件内容文本（超过 400 行时截断）
     */
    @Tool("读取项目中指定源码文件的完整内容，需要传入相对项目根目录的路径，例如 yudao-module-ai-lc4j/pom.xml 或 src/main/resources/application.yaml")
    public String readProjectFile(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null) {
            return "路径无效：" + relativePath;
        }
        if (!Files.isRegularFile(file)) {
            return "文件不存在：" + relativePath;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        if (!SEARCHABLE_EXTENSIONS.contains(ext)) {
            return "不支持读取该类型文件（" + ext + "），仅支持文本类源码文件";
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return "（空文件）";
            }
            StringBuilder sb = new StringBuilder("【文件内容】").append(relativePath).append("\n");
            int limit = Math.min(lines.size(), MAX_FILE_LINES);
            for (int i = 0; i < limit; i++) {
                sb.append(String.format("%4d | %s%n", i + 1, lines.get(i)));
            }
            if (lines.size() > MAX_FILE_LINES) {
                sb.append("...（共 ").append(lines.size()).append(" 行，仅展示前 ")
                        .append(MAX_FILE_LINES).append(" 行）");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("[readProjectFile][读取文件失败 {}]", relativePath, e);
            return "读取文件失败：" + e.getMessage();
        }
    }

    /**
     * 在项目代码中按关键字搜索。
     *
     * @param keyword 搜索关键字，大小写不敏感
     * @return 匹配的文件、行号与该行内容
     */
    @Tool("在项目全部源码中按关键字搜索（大小写不敏感），返回匹配的文件、行号和该行内容，用于定位类、方法或配置项在哪里定义")
    public String searchProjectCode(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "请提供搜索关键字";
        }
        String target = keyword.trim().toLowerCase();
        List<String> results = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // 跳过构建产物等目录
                    return SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_SEARCH_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
                    if (!SEARCHABLE_EXTENSIONS.contains(ext)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            if (results.size() >= MAX_SEARCH_RESULTS) {
                                break;
                            }
                            if (lines.get(i).toLowerCase().contains(target)) {
                                String relative = root.relativize(file).toString().replace('\\', '/');
                                results.add(String.format("%s:%d: %s", relative, i + 1, lines.get(i).trim()));
                            }
                        }
                    } catch (IOException e) {
                        // 非 UTF-8 文件等读取异常，跳过
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("[searchProjectCode][搜索失败 keyword={}]", keyword, e);
            return "搜索失败：" + e.getMessage();
        }
        if (results.isEmpty()) {
            return "未找到包含「" + keyword + "」的代码";
        }
        StringBuilder sb = new StringBuilder("【搜索结果：").append(keyword).append("】\n");
        for (String r : results) {
            sb.append("- ").append(r).append("\n");
        }
        if (results.size() >= MAX_SEARCH_RESULTS) {
            sb.append("...（结果过多已截断，建议用更精确的关键字）");
        }
        return sb.toString();
    }

    /**
     * 解析相对路径为项目内的绝对路径，越界（如 ../）时返回 null
     */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty() || ".".equals(relativePath.trim())) {
            return root;
        }
        Path resolved = root.resolve(relativePath.trim()).normalize();
        // 防目录穿越：解析结果必须仍在项目根目录内
        if (!resolved.startsWith(root)) {
            log.warn("[resolve][拒绝越界路径 {}]", relativePath);
            return null;
        }
        return resolved;
    }

    /**
     * 判断路径的各级目录是否均不属于需跳过的目录
     */
    private boolean isNotSkipped(Path path) {
        for (Path part : path) {
            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

}

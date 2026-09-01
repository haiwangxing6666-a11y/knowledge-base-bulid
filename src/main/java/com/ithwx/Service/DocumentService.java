package com.ithwx.Service;

import com.ithwx.Entity.DocumentEntity;
import com.ithwx.Repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件、笔记和网页的统一入库服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> SUPPORTED_FILE_TYPES =
            Set.of("pdf", "txt", "md", "docx");

    private final DocumentParserService parserService;
    private final ChunkingService chunkingService;
    private final WebContentService webContentService;
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    // ==================== public 业务入口 ====================

    /**
     * 查询所有资料，按照上传时间倒序排列。
     */
    public List<DocumentEntity> list() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadTime")
        );
    }

    /**
     * 上传并入库文件。
     */
    public DocumentEntity ingest(MultipartFile file) {
        validateFile(file);

        String originalName = resolveName(file);
        String fileType = extractType(originalName);

        validateFileType(fileType);

        String text;

        try {

            text = parserService.parse(file, fileType);

        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析文件：" + safeMessage(exception), exception);
        }

        return ingestNew(originalName, fileType, null, text
        );
    }

    /**
     * 新建并入库纯文本笔记。
     */
    public DocumentEntity ingestNote(String title, String content) {
        return ingestNew(title.trim(), "note", null, content);
    }

    /**
     * 抓取并入库公开网页。
     */
    public DocumentEntity ingestLink(String url, String preferredTitle) {
        WebContentService.WebPage page = webContentService.fetch(url);

        String title = preferredTitle == null || preferredTitle.isBlank() ? page.title() : preferredTitle.trim();

        String safeTitle = title.substring(0, Math.min(title.length(), 200));

        return ingestNew(safeTitle, "url", page.url(), page.text());
    }

    /**
     * 使用新文件替换已有资料的内容。
     */
    public DocumentEntity reingest(Long id, MultipartFile file) {
        validateFile(file);

        DocumentEntity entity = requireDocument(id);

        String originalName = resolveName(file);
        String fileType = extractType(originalName);

        validateFileType(fileType);

        String text;

        try {
            text = parserService.parse(file, fileType);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析文件：" + safeMessage(exception), exception);
        }

        String newHash = hash(text);

        if (
                "READY".equals(entity.getStatus()) && newHash.equals(entity.getContentHash())
        ) {
            return entity;
        }

        entity.setName(originalName);
        entity.setFilePath(originalName);
        entity.setFileType(fileType);
        entity.setSourceUrl(null);

        return replaceContent(entity, text, newHash);
    }

    /**
     * 删除资料元数据和对应向量。
     */
    public void delete(Long id) {
        requireDocument(id);
        deleteChunks(id);
        documentRepository.deleteById(id);

        log.info("已删除资料: {}", id);
    }

    // ==================== private 入库核心流程 ====================

    /**
     * 新资料入库。
     */
    private DocumentEntity ingestNew(String name, String type, String sourceUrl, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("资料内容不能为空");
        }

        DocumentEntity entity = new DocumentEntity();

        entity.setName(name);

        entity.setFilePath(type.equals("url") ? sourceUrl : name);

        entity.setFileType(type);
        entity.setSourceUrl(sourceUrl);
        entity.setUploadTime(LocalDateTime.now());
        entity.setStatus("PROCESSING");
        entity.setChunkCount(0);
        entity.setContentHash(hash(text));

        documentRepository.save(entity);

        return index(entity, text);
    }

    /**
     * 替换已有资料内容。
     */
    private DocumentEntity replaceContent(DocumentEntity entity, String text, String contentHash
    ) {
        entity.setUploadTime(LocalDateTime.now());
        entity.setStatus("PROCESSING");
        entity.setChunkCount(0);
        entity.setContentHash(contentHash);

        documentRepository.save(entity);

        deleteChunks(entity.getId());

        return index(entity, text);
    }

    /**
     * 切分正文、生成向量并写入 pgvector。
     */
    private DocumentEntity index(DocumentEntity entity, String text) {
        try {
            List<String> chunks = chunkingService.chunk(text);

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("资料解析后没有可入库的内容");
            }

            List<Document> documents = new ArrayList<>(chunks.size());

            for (int index = 0; index < chunks.size(); index++) {
                Map<String, Object> metadata =
                        Map.of(
                                "documentId", String.valueOf(entity.getId()),
                                "chunkIndex", index,
                                "sourceName", entity.getName(),
                                "sourceUrl", entity.getSourceUrl() == null ? "" : entity.getSourceUrl(),
                                "fileType", entity.getFileType()
                        );

                Document document = new Document(chunks.get(index), metadata);

                documents.add(document);
            }

            vectorStore.add(documents);

            entity.setChunkCount(chunks.size());
            entity.setStatus("READY");

            documentRepository.save(entity);

            log.info(
                    "入库完成: {} -> {} 个 chunk",
                    entity.getName(),
                    chunks.size()
            );

            return entity;

        } catch (Exception exception) {
            entity.setStatus("FAILED");
            documentRepository.save(entity);

            log.error(
                    "入库失败: {}",
                    entity.getName(),
                    exception
            );

            throw new RuntimeException("资料入库失败：" + entity.getName(), exception);
        }
    }

    // ==================== private 查询与删除辅助方法 ====================

    /**
     * 根据 ID 查询资料，不存在时抛出异常。
     */
    private DocumentEntity requireDocument(Long id) {
        return documentRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("资料不存在：" + id)
                );
    }

    /**
     * 删除某一资料对应的全部向量块。
     */
    private void deleteChunks(Long documentId) {
        Filter.Expression filter =
                new FilterExpressionBuilder().eq(
                                "documentId",
                                String.valueOf(documentId)
                        ).build();

        vectorStore.delete(filter);
    }

    // ==================== private 文件校验辅助方法 ====================

    /**
     * 检查上传文件是否为空。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "请选择非空文件"
            );
        }
    }

    /**
     * 检查文件类型是否受支持。
     */
    private void validateFileType(String fileType) {
        if (
                !SUPPORTED_FILE_TYPES.contains(
                        fileType
                )
        ) {
            throw new IllegalArgumentException(
                    "不支持的文件格式："
                            + fileType
                            + "，仅支持 pdf/txt/md/docx"
            );
        }
    }

    /**
     * 取得安全的文件名。
     */
    private String resolveName(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            return "未命名文件.txt";
        }

        String normalized = originalName.replace('\\', '/');

        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    /**
     * 从文件名中取得扩展名。
     */
    private String extractType(String filename) {
        if (!filename.contains(".")) {
            return "";
        }

        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 计算正文的 SHA-256 摘要。
     */
    private String hash(String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] bytes = messageDigest.digest(
                    text.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);

        } catch (Exception exception) {
            throw new IllegalStateException("无法计算内容摘要", exception);
        }
    }

    /**
     * 安全取得异常信息。
     */
    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
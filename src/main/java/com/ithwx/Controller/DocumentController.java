package com.ithwx.Controller;

import com.ithwx.Dto.LinkRequest;
import com.ithwx.Dto.NoteRequest;
import com.ithwx.Entity.DocumentEntity;
import com.ithwx.Service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文件并进行向量化入库。
     */
    @PostMapping
    public ResponseEntity<DocumentEntity> upload(@RequestParam("file") MultipartFile file) {
        DocumentEntity entity = documentService.ingest(file);

        return ResponseEntity.ok(entity);
    }

    /**
     * 新建纯文本笔记并进行向量化入库。
     */
    @PostMapping("/notes")
    public ResponseEntity<DocumentEntity> createNote(@Valid @RequestBody NoteRequest request) {
        DocumentEntity entity = documentService.ingestNote(
                        request.title(),
                        request.content()
                );

        return ResponseEntity.ok(entity);
    }

    /**
     * 抓取公开网页并进行向量化入库。
     */
    @PostMapping("/links")
    public ResponseEntity<DocumentEntity> createLink(@Valid @RequestBody LinkRequest request
    ) {
        DocumentEntity entity = documentService.ingestLink(
                        request.url(),
                        request.title()
                );

        return ResponseEntity.ok(entity);
    }

    /**
     * 查询全部资料。
     */
    @GetMapping
    public List<DocumentEntity> list() {
        return documentService.list();
    }

    /**
     * 用新文件重新处理已有资料。
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentEntity> reingest(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) {
        DocumentEntity entity = documentService.reingest(id, file);

        return ResponseEntity.ok(entity);
    }

    /**
     * 删除资料及对应向量。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable
            Long id
    ) {
        documentService.delete(id);

        return ResponseEntity
                .noContent()
                .build();
    }
}
package com.ithwx.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String filePath;

    private String fileType;

    @Column(length = 2048)
    private String sourceUrl;

    @Column(length = 64)
    private String contentHash;

    private LocalDateTime uploadTime;

    private String status;

    private Integer chunkCount;
}
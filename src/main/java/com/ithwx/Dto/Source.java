package com.ithwx.Dto;

/**
 * 回答的来源信息。
 */
public record Source(
        Long documentId,
        String sourceName,
        String sourceUrl,
        Integer chunkIndex,
        String snippet,
        Double score
) {
}
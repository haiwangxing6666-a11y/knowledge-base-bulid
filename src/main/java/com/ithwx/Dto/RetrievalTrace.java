package com.ithwx.Dto;

/**
 * 本次 RAG 的检索过程。
 */
public record RetrievalTrace(
        String originalQuery,
        String rewrittenQuery,
        boolean retried,
        int candidateCount,
        int acceptedCount
) {
}
package com.ithwx.Dto;

import java.util.List;

/**
 * 问答接口返回结果。
 */
public record ChatResponse(
        String answer,
        List<Source> sources,
        RetrievalTrace retrieval
) {
}
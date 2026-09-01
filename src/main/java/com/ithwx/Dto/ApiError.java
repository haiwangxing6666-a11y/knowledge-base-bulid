package com.ithwx.Dto;

import java.time.Instant;

/**
 * API 统一错误响应。
 */
public record ApiError(
        Instant timestamp,
        int status,
        String message,
        String path
) {
}
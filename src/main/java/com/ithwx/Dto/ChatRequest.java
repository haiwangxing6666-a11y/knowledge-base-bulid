package com.ithwx.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户提问请求。
 */
public record ChatRequest(

        @NotBlank(message = "问题不能为空")
        @Size(
                max = 2000,
                message = "问题不能超过 2000 个字符"
        )
        String question

) {
}
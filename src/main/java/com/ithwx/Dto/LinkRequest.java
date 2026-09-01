package com.ithwx.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 收录网页时接收的请求参数。
 */
public record LinkRequest(

        @NotBlank(message = "网址不能为空")
        @Size(max = 2048, message = "网址不能超过 2048 个字符")
        String url,

        @Size(max = 200, message = "标题不能超过 200 个字符")
        String title

) {
}
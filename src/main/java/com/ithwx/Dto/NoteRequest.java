package com.ithwx.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建笔记时接收的请求参数。
 */
public record NoteRequest(

        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 个字符")
        String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 200_000, message = "内容不能超过 200000 个字符")
        String content

) {
}
package com.ithwx.Service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentParserServiceTest {

    private final DocumentParserService parserService =
            new DocumentParserService();

    @Test
    void shouldParseTxtFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "实验室知识库测试内容".getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String result =
                parserService.parse(file, "txt");

        assertEquals(
                "实验室知识库测试内容",
                result
        );
    }

    @Test
    void shouldParseMarkdownFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "README.md",
                "text/markdown",
                "# 项目说明".getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String result =
                parserService.parse(file, "md");

        assertEquals("# 项目说明", result);
    }

    @Test
    void shouldIgnoreUppercaseFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.TXT",
                "text/plain",
                "大写后缀测试".getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String result =
                parserService.parse(file, "TXT");

        assertEquals("大写后缀测试", result);
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> parserService.parse(
                                file,
                                "jpg"
                        )
                );

        assertEquals(
                "不支持的文件格式：jpg",
                exception.getMessage()
        );
    }
}
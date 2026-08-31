package com.ithwx.Service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class DocumentParserService {


    public String parse(MultipartFile file, String fileType) throws Exception {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePdf(file);
            case "txt", "md" -> parseText(file);
            case "docx" -> parseDocx(file);
            default -> throw new IllegalArgumentException(
                    "不支持的文件格式：" + fileType
            );
        };
    }


    private String parsePdf(MultipartFile file) throws Exception {
        try (
                InputStream inputStream = file.getInputStream();//获取上传文件的输入流，读取文件二进制数据。
                //PDFBox 加载字节数组，得到`PDDocument`文档对象，代表整个 PDF 文档。
                PDDocument document =
                        Loader.loadPDF(inputStream.readAllBytes())//：一次性把整个 PDF 全部读成字节数组 `byte[]`。
        ) {
            PDFTextStripper stripper = new PDFTextStripper();//`PDFTextStripper`：PDFBox 专门抽取 PDF 文本的工具类。
            stripper.setSortByPosition(true);//按页面坐标排序文字
            return stripper.getText(document);
        }
    }


    private String parseText(MultipartFile file) throws Exception {
        return new String(
                file.getBytes(),
                StandardCharsets.UTF_8
        );
    }


    private String parseDocx(MultipartFile file) throws Exception {
        StringBuilder textBuilder = new StringBuilder();

        try (
                InputStream inputStream = file.getInputStream();
                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                textBuilder
                        .append(paragraph.getText())
                        .append("\n");
            }
        }

        return textBuilder.toString();
    }
}
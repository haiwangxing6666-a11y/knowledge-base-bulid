package com.ithwx.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切分服务。
 *
 * 切分策略：
 * 1. 先按照空行划分段落；
 * 2. 将多个段落合并到一个 chunk；
 * 3. 每个 chunk 不超过 maxChars；
 * 4. 相邻 chunk 保留 overlapChars 个重叠字符；
 * 5. 单个段落过长时进行硬切分。
 */
@Slf4j
@Service
public class ChunkingService {

    @Value("${app.chunk.max-chars:500}")
    private int maxChars;

    @Value("${app.chunk.overlap-chars:50}")
    private int overlapChars;

    /**
     * 文本切分的公开入口。
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        if (maxChars <= 0) {
            throw new IllegalStateException(
                    "app.chunk.max-chars 必须大于 0"
            );
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : splitParagraphs(text)) {

            // 单个段落已经超过最大长度，直接进行硬切分
            if (paragraph.length() > maxChars) {
                flush(current, chunks);
                chunks.addAll(hardSplit(paragraph));
                continue;
            }

            // 当前文本块无法再放入下一段
            if (current.length() > 0 && current.length() + paragraph.length() + 1 > maxChars) {
                flush(current, chunks);

                // 将上一个文本块的末尾作为新块开头
                current.append(overlapTail(chunks));
            }

            // 不同段落之间添加换行
            if (current.length() > 0) {
                current.append('\n');
            }

            current.append(paragraph);
        }

        // 保存最后一个还没有放入集合的文本块
        flush(current, chunks);

        return chunks;
    }

    /**
     * 按照空行分割段落。
     */
    private List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();

        for (
                String paragraph :
                text.split("\\r?\\n\\s*\\r?\\n")
        ) {
            String trimmed = paragraph.trim();

            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(text.trim());
        }

        return paragraphs;
    }

    /**
     * 将当前文本块保存到集合中，然后清空 current。
     */
    private void flush(StringBuilder current, List<String> chunks) {
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    /**
     * 取得上一个文本块末尾的重叠内容。
     */
    private String overlapTail(List<String> chunks) {
        if (chunks.isEmpty() || overlapChars <= 0) {
            return "";
        }

        String previous =
                chunks.get(chunks.size() - 1);

        int effectiveOverlap = Math.min(
                overlapChars,
                Math.max(0, maxChars - 1)
        );

        if (previous.length() > effectiveOverlap) {
            return previous.substring(
                    previous.length() - effectiveOverlap
            );
        }

        return previous;
    }

    /**
     * 对超过最大长度的单个段落进行强制切分。
     */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();

        int effectiveOverlap = Math.min(
                Math.max(0, overlapChars),
                Math.max(0, maxChars - 1)
        );

        int step = maxChars - effectiveOverlap;

        for (
                int start = 0;
                start < text.length();
                start += step
        ) {
            int end = Math.min(
                    text.length(),
                    start + maxChars
            );

            result.add(text.substring(start, end));

            if (end == text.length()) {
                break;
            }
        }

        return result;
    }
}
package com.ithwx.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private ChunkingService service;

    @BeforeEach
    void setUp() {
        service = new ChunkingService();

        ReflectionTestUtils.setField(
                service,
                "maxChars",
                10
        );

        ReflectionTestUtils.setField(
                service,
                "overlapChars",
                3
        );
    }

    @Test
    void returnsNoChunksForBlankText() {
        assertThat(service.chunk("  \n "))
                .isEmpty();
    }

    @Test
    void keepsOverlapWhenParagraphStartsANewChunk() {
        List<String> chunks = service.chunk(
                "12345\n\n67890\n\nabc"
        );

        assertThat(chunks).containsExactly(
                "12345",
                "345\n67890",
                "890\nabc"
        );

        assertThat(chunks).allMatch(
                chunk -> chunk.length() <= 10
        );
    }

    @Test
    void hardSplitDoesNotCreateRedundantTinyTail() {
        List<String> chunks =
                service.chunk("abcdefghijklmnop");

        assertThat(chunks).containsExactly(
                "abcdefghij",
                "hijklmnop"
        );
    }
}
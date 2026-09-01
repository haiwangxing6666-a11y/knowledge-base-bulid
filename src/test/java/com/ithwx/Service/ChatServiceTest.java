package com.ithwx.Service;

import com.ithwx.Dto.ChatRequest;
import com.ithwx.Dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private VectorStore vectorStore;
    private ChatModel chatModel;
    private ChatService service;

    @BeforeEach
    void setUp() {
        vectorStore =
                mock(VectorStore.class);

        chatModel =
                mock(ChatModel.class);

        service =
                new ChatService(
                        vectorStore,
                        chatModel
                );

        ReflectionTestUtils.setField(
                service,
                "topK",
                3
        );

        ReflectionTestUtils.setField(
                service,
                "minSimilarity",
                0.6
        );

        ReflectionTestUtils.setField(
                service,
                "retryMinHits",
                2
        );

        ReflectionTestUtils.setField(
                service,
                "maxContextChars",
                1000
        );
    }

    @Test
    void answersDirectlyWhenFirstRetrievalIsGoodEnough() {
        when(
                vectorStore.similaritySearch(
                        any(SearchRequest.class)
                )
        ).thenReturn(
                List.of(
                        document(
                                "1",
                                0,
                                "第一条依据",
                                0.90
                        ),
                        document(
                                "1",
                                1,
                                "第二条依据",
                                0.80
                        )
                )
        );

        when(
                chatModel.call(anyString())
        ).thenReturn(
                "有依据的回答【资料1】"
        );

        ChatResponse response =
                service.ask(
                        new ChatRequest("问题")
                );

        assertThat(response.answer())
                .contains("资料1");

        assertThat(response.sources())
                .hasSize(2);

        assertThat(
                response.retrieval().retried()
        ).isFalse();

        verify(
                vectorStore,
                times(1)
        ).similaritySearch(
                any(SearchRequest.class)
        );

        verify(
                chatModel,
                times(1)
        ).call(anyString());
    }

    @Test
    void rewritesAndRetriesWhenFirstRetrievalIsWeak() {
        when(
                vectorStore.similaritySearch(
                        any(SearchRequest.class)
                )
        )
                .thenReturn(
                        List.of(
                                document(
                                        "1",
                                        0,
                                        "噪声",
                                        0.30
                                )
                        )
                )
                .thenReturn(
                        List.of(
                                document(
                                        "2",
                                        0,
                                        "可靠依据",
                                        0.88
                                )
                        )
                );

        when(
                chatModel.call(anyString())
        ).thenReturn(
                "更适合检索的问题",
                "二次检索后的回答【资料1】"
        );

        ChatResponse response =
                service.ask(
                        new ChatRequest(
                                "它该怎么评估？"
                        )
                );

        assertThat(response.sources())
                .singleElement()
                .satisfies(
                        source ->
                                assertThat(
                                        source.documentId()
                                ).isEqualTo(2L)
                );

        assertThat(
                response.retrieval().retried()
        ).isTrue();

        assertThat(
                response.retrieval()
                        .rewrittenQuery()
        ).isEqualTo(
                "更适合检索的问题"
        );

        verify(
                vectorStore,
                times(2)
        ).similaritySearch(
                any(SearchRequest.class)
        );

        verify(
                chatModel,
                times(2)
        ).call(anyString());
    }

    @Test
    void refusesToAnswerWhenBothRetrievalsHaveNoEvidence() {
        when(
                vectorStore.similaritySearch(
                        any(SearchRequest.class)
                )
        ).thenReturn(List.of());

        when(
                chatModel.call(anyString())
        ).thenReturn("改写后的问题");

        ChatResponse response =
                service.ask(
                        new ChatRequest(
                                "知识库之外的问题"
                        )
                );

        assertThat(response.answer())
                .contains("无法回答");

        assertThat(response.sources())
                .isEmpty();

        assertThat(
                response.retrieval().retried()
        ).isTrue();

        verify(
                vectorStore,
                times(2)
        ).similaritySearch(
                any(SearchRequest.class)
        );

        // 只调用一次，用于改写问题；
        // 因为没有依据，所以不会调用模型生成答案
        verify(
                chatModel,
                times(1)
        ).call(anyString());
    }

    private Document document(
            String documentId,
            int chunkIndex,
            String text,
            double score
    ) {
        return Document.builder()
                .text(text)
                .metadata(
                        Map.of(
                                "documentId",
                                documentId,
                                "chunkIndex",
                                chunkIndex,
                                "sourceName",
                                "测试资料",
                                "sourceUrl",
                                ""
                        )
                )
                .score(score)
                .build();
    }
}
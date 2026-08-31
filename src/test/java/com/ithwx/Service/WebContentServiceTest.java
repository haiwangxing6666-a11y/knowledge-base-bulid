package com.ithwx.Service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebContentServiceTest {

    private final WebContentService service =
            new WebContentService();

    @Test
    void rejectsFileProtocol() {
        assertThatThrownBy(
                () -> service.fetch(
                        "file:///C:/Windows/System32/test.txt"
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "只支持公开的 http/https 网址"
                );
    }

    @Test
    void rejectsLoopbackAddress() {
        assertThatThrownBy(
                () -> service.fetch(
                        "http://127.0.0.1:8080/private"
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "不允许访问本机或内网地址"
                );
    }

    @Test
    void rejectsPrivateNetworkAddress() {
        assertThatThrownBy(
                () -> service.fetch(
                        "http://192.168.1.1/admin"
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "不允许访问本机或内网地址"
                );
    }
}
package com.ithwx.Service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 抓取公开网页正文。
 *
 * 限制协议、内网地址、响应大小和超时，
 * 降低 SSRF 风险。
 */
@Service
public class WebContentService {

                //HttpClient 是 Java 自带的 HTTP 客户端，负责访问网页
    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    @Value("${app.web.max-content-bytes:2097152}")
    private int maxContentBytes;

    /**
     * 抓取网页并提取正文。
     */
    public WebPage fetch(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());

            validatePublicHttpUrl(uri);

            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(10))
                            .header(
                                    "User-Agent",
                                    "KnowledgeBaseBot/1.0"
                            )
                            .header(
                                    "Accept",
                                    "text/html,text/plain;q=0.9"
                            )
                            .GET()
                            .build();

            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                throw new IllegalArgumentException(
                        "网页返回状态码 "
                                + response.statusCode()
                );
            }

            String contentType =
                    response.headers()
                            .firstValue("content-type")
                            .orElse("")
                            .toLowerCase();

            if (
                    !contentType.contains("text/html")
                            && !contentType.contains("text/plain")
            ) {
                throw new IllegalArgumentException(
                        "网页内容类型不受支持："
                                + contentType
                );
            }

            byte[] bytes;

            try (InputStream body = response.body()) {
                bytes = body.readNBytes(
                        maxContentBytes + 1
                );
            }

            if (bytes.length > maxContentBytes) {
                throw new IllegalArgumentException(
                        "网页正文超过大小限制"
                );
            }

            String raw = new String(
                    bytes,
                    StandardCharsets.UTF_8
            );

            if (contentType.contains("text/plain")) {
                return new WebPage(
                        uri.toString(),
                        uri.getHost(),
                        raw
                );
            }

            org.jsoup.nodes.Document html =
                    Jsoup.parse(raw, uri.toString());

            html.select(
                    "script,style,noscript,svg,nav,footer"
            ).remove();

            Element main =
                    html.selectFirst("main,article");

            String text =
                    (main == null ? html.body() : main)
                            .text();

            String title =
                    html.title().isBlank()
                            ? uri.getHost()
                            : html.title();

            if (text.isBlank()) {
                throw new IllegalArgumentException(
                        "网页中没有可提取的正文"
                );
            }

            return new WebPage(
                    uri.toString(),
                    title,
                    text
            );

        } catch (IllegalArgumentException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "抓取网页失败："
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 验证 URL 是否为公开的 HTTP/HTTPS 地址。
     */
    private void validatePublicHttpUrl(
            URI uri
    ) throws Exception {
        String scheme = uri.getScheme();

        boolean supportedProtocol =
                "http".equalsIgnoreCase(scheme)
                        || "https".equalsIgnoreCase(scheme);

        if (
                !supportedProtocol
                        || uri.getHost() == null
        ) {
            throw new IllegalArgumentException(
                    "只支持公开的 http/https 网址"
            );
        }

        InetAddress[] addresses =
                InetAddress.getAllByName(
                        uri.getHost()
                );

        for (InetAddress address : addresses) {
            if (
                    address.isAnyLocalAddress()
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress()
                            || address.isMulticastAddress()
            ) {
                throw new IllegalArgumentException(
                        "不允许访问本机或内网地址"
                );
            }
        }
    }

    /**
     * 网页抓取结果。
     */
    public record WebPage(
            String url,
            String title,
            String text
    ) {
    }
}
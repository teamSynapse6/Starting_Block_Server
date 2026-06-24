package com.startingblock.domain.announcement.application;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class KStartupAttachmentExtractor {

    private static final String K_STARTUP_BASE_URL = "https://www.k-startup.go.kr";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Optional<String> extractFirstDownloadUrl(final String detailUrl) {
        if (detailUrl == null || detailUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(detailUrl.trim()))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("K-Startup 첨부파일 HTML 조회 실패 status={}, detailUrl={}", response.statusCode(), detailUrl);
                return Optional.empty();
            }

            Document document = Jsoup.parse(response.body(), detailUrl);
            Element downloadLink = document.selectFirst(".board_file a[name=downloadBtn].btn_down[href]");
            if (downloadLink == null) {
                return Optional.empty();
            }

            String href = downloadLink.attr("href");
            if (href == null || href.isBlank() || href.startsWith("javascript:")) {
                return Optional.empty();
            }
            return Optional.of(resolveUrl(href.trim()));
        } catch (Exception exception) {
            log.warn("K-Startup 첨부파일 URL 추출 실패 detailUrl={}", detailUrl, exception);
            return Optional.empty();
        }
    }

    private String resolveUrl(final String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("/")) {
            return K_STARTUP_BASE_URL + href;
        }
        return K_STARTUP_BASE_URL + "/" + href;
    }
}

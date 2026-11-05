package dev.beatward.notification;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class XizhiNotificationSender implements UserChannelSender {

    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int RESPONSE_TIMEOUT_MS = 10000;

    private final CloseableHttpClient httpClient;
    private final NotificationEndpointProperties endpoints;

    public XizhiNotificationSender(
            @Qualifier("notificationHttpClient") CloseableHttpClient httpClient,
            NotificationEndpointProperties endpoints) {
        this.httpClient = httpClient;
        this.endpoints = endpoints;
    }

    @Override
    public NotificationChannel type() {
        return NotificationChannel.XIZHI;
    }

    @Override
    public void send(Map<String, String> credentials, String title, String content) {
        String key = UriUtils.encodePathSegment(credentials.get("key"), StandardCharsets.UTF_8);
        String body = "title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&content=" + URLEncoder.encode(content, StandardCharsets.UTF_8);

        HttpPost request = new HttpPost(endpoints.xizhiApiBaseUrl() + "/" + key + ".send");
        request.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(RESPONSE_TIMEOUT_MS))
                .build());
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                throw new NotificationSendException("Xizhi returned HTTP " + status,
                        status == 429 || status >= 500);
            }
        } catch (IOException ex) {
            throw new NotificationSendException(
                    "Xizhi request failed: " + ex.getClass().getSimpleName(), true, ex);
        }
    }
}

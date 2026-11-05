package dev.beatward.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PushPlusNotificationSender implements UserChannelSender {

    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int RESPONSE_TIMEOUT_MS = 10000;

    private final CloseableHttpClient httpClient;
    private final NotificationEndpointProperties endpoints;
    private final ObjectMapper objectMapper;

    public PushPlusNotificationSender(
            @Qualifier("notificationHttpClient") CloseableHttpClient httpClient,
            NotificationEndpointProperties endpoints, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.endpoints = endpoints;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel type() {
        return NotificationChannel.PUSHPLUS;
    }

    @Override
    public void send(Map<String, String> credentials, String title, String content) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "token", credentials.get("token"),
                    "title", title,
                    "content", content,
                    "template", "txt"));
        } catch (Exception ex) {
            throw new NotificationSendException("Failed to build PushPlus request", false);
        }

        HttpPost request = new HttpPost(endpoints.pushplusApiBaseUrl() + "/send");
        request.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(RESPONSE_TIMEOUT_MS))
                .build());
        request.setEntity(new StringEntity(payload,
                ContentType.create("application/json", StandardCharsets.UTF_8)));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int status = response.getCode();
            String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) {
                throw new NotificationSendException("PushPlus returned HTTP " + status,
                        status == 429 || status >= 500);
            }
            verifyResponseCode(body);
        } catch (IOException | ParseException ex) {
            throw new NotificationSendException(
                    "PushPlus request failed: " + ex.getClass().getSimpleName(), true, ex);
        }
    }

    private void verifyResponseCode(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root.get("code");
            if (code != null && code.isNumber() && code.asInt() != 200) {
                throw new NotificationSendException(
                        "PushPlus returned code " + code.asInt(), code.asInt() == 900);
            }
        } catch (IOException ignored) {
            // Non-JSON success bodies are accepted when the HTTP status is 2xx.
        }
    }
}

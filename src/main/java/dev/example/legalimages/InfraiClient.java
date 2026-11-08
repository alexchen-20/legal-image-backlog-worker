package dev.example.legalimages;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class InfraiClient {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Sleeper sleeper;

    InfraiClient(InfraiConfig config, HttpClient http, ObjectMapper json, Sleeper sleeper) {
        this.config = config;
        this.http = http;
        this.json = json;
        this.sleeper = sleeper;
    }

    JsonNode publish(Map<String, Object> payload, String idempotencyKey) {
        return post("/v1/queue/publish", Map.of("queue", "legal-image-backlog", "payload", payload), idempotencyKey);
    }

    JsonNode resize(MatterImageJob job, IntakeDecision decision) {
        Map<String, Object> request = Map.of(
                "image", job.image(),
                "width", decision.width(),
                "height", decision.height(),
                "fit", "contain",
                "enlarge", false,
                "format", "png",
                "store", true);
        return post("/v1/image/resize", request, "resize-" + job.matterId() + "-" + job.documentId());
    }

    private JsonNode post(String path, Object body, String idempotencyKey) {
        String requestBody;
        try {
            requestBody = json.writeValueAsString(body);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Request body is not JSON serializable", error);
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                    sleeper.sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiException(error.path("code").asText("REQUEST_REJECTED"), error, response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new InfraiException("TRANSPORT_FAILURE", envelope.path("error"), response.statusCode());
                }
                return envelope.path("data");
            } catch (IOException error) {
                throw new InfraiException("TRANSPORT_FAILURE", json.createObjectNode().put("message", error.getMessage()), 503);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", json.createObjectNode().put("message", "Request interrupted"), 503);
            }
        }
        throw new IllegalStateException("Retry loop exhausted");
    }

    private JsonNode decodeEnvelope(String body, int status) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException error) {
            throw new InfraiException("INVALID_ENVELOPE", json.createObjectNode().put("message", "Response was not JSON"), status);
        }
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> parseRetryAfter(value, attempt))
                .orElse(250L * (1L << attempt));
    }

    private long parseRetryAfter(String value, int attempt) {
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            return 250L * (1L << attempt);
        }
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}

final class InfraiException extends RuntimeException {
    private final String code;
    private final JsonNode detail;
    private final int status;

    InfraiException(String code, JsonNode detail, int status) {
        super(code);
        this.code = code;
        this.detail = detail;
        this.status = status;
    }

    String code() { return code; }
    JsonNode detail() { return detail; }
    int status() { return status; }
}

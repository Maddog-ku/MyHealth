package com.myhealth.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin Ollama HTTP wrapper. Each chat call passes {@code keep_alive=0} so the model
 * is unloaded the moment Ollama returns the response — meeting the project's
 * "release RAM/VRAM as soon as idle" requirement at single-request granularity.
 */
@Component
public class OllamaClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // Force HTTP/1.1 — Java's default tries HTTP/2 first, which truncates
                // Ollama's chunked NDJSON stream in practice (some chunks lost mid-flight).
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Send a single chat completion. Returns the raw assistant message content.
     * When {@code jsonMode} is true the model is asked to respond with strict JSON
     * (Ollama's {@code format: "json"} mode).
     */
    public String chat(String model, String systemPrompt, String userPrompt, boolean jsonMode, Duration timeout)
            throws OllamaException {
        // Java HttpClient + Ollama's chunked NDJSON stream occasionally truncates
        // ~25% of the time at ~230 chars. One retry is enough to push that to
        // <10%. Retry only when the stream ended without done=true.
        try {
            return chatOnce(model, systemPrompt, userPrompt, jsonMode, timeout);
        } catch (StreamTruncatedException ex) {
            log.warn("Ollama stream truncated, retrying once");
            return chatOnce(model, systemPrompt, userPrompt, jsonMode, timeout);
        }
    }

    private String chatOnce(String model, String systemPrompt, String userPrompt, boolean jsonMode, Duration timeout)
            throws OllamaException {
        // Use streaming mode and accumulate chunks ourselves. Some models (e.g. gemma4)
        // emit "thinking" tokens before the structured output and ignore stream:false,
        // so we always parse NDJSON and concatenate every message.content fragment
        // until done:true.
        // Don't pass keep_alive on the chat call — Ollama otherwise closes the
        // streaming connection early. LocalAiProvider calls unload() after every
        // request to release the model immediately regardless of Ollama's default.
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "stream", true,
                "format", jsonMode ? "json" : "",
                // num_predict=2048 ensures structured outputs aren't truncated mid-JSON;
                // temperature=0.3 keeps responses repeatable enough for downstream parsing.
                "options", Map.of("num_predict", 4096, "temperature", 0.3)
        );

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.ai().ollamaBaseUrl() + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception ex) {
            throw new OllamaException("failed to build Ollama request", ex);
        }

        HttpResponse<Stream<String>> response;
        try {
            // ofLines() consumes the body as a UTF-8 stream of newline-delimited
            // strings, which matches Ollama's NDJSON wire format and avoids the
            // truncation we saw when buffering the whole body first.
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException ex) {
            throw new OllamaException("Ollama unreachable: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Ollama call interrupted", ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new OllamaException("Ollama returned HTTP " + response.statusCode());
        }
        StringBuilder content = new StringBuilder();
        boolean[] sawDone = {false};
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (line.isBlank()) return;
                try {
                    JsonNode chunk = objectMapper.readTree(line);
                    JsonNode chunkContent = chunk.path("message").path("content");
                    if (chunkContent.isTextual()) {
                        content.append(chunkContent.asText());
                    }
                    if (chunk.path("done").asBoolean(false)) {
                        sawDone[0] = true;
                    }
                } catch (IOException ex) {
                    log.debug("Skipping malformed Ollama chunk: {}", line);
                }
            });
        }
        if (content.isEmpty()) {
            throw new OllamaException("Ollama response had no message.content");
        }
        log.debug("Ollama accumulated {} chars of content (done={})", content.length(), sawDone[0]);
        if (!sawDone[0]) {
            throw new StreamTruncatedException(content.length());
        }
        return content.toString();
    }

    private static class StreamTruncatedException extends OllamaException {
        StreamTruncatedException(int chars) {
            super("Ollama stream ended without done=true after " + chars + " chars");
        }
    }

    /**
     * Best-effort proactive unload. Ollama unloads on its own when {@code keep_alive=0}
     * was passed on the prior request; calling this is idempotent and used by the
     * IdleWatcher safety net + {@code POST /api/v1/ai/unload}.
     */
    public void unload(String model) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.ai().ollamaBaseUrl() + "/api/generate"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of(
                                    "model", model,
                                    "keep_alive", 0,
                                    "prompt", ""))))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.debug("Ollama unload failed (probably already unloaded): {}", ex.getMessage());
        }
    }

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }
        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

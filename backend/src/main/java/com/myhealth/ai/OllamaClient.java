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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public OllamaClient(AppProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // Force HTTP/1.1 — Java's default tries HTTP/2 first, which truncates
                // Ollama's chunked NDJSON stream in practice (some chunks lost mid-flight).
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    /** Test-only seam: inject a mock HttpClient. Not used by Spring autowiring. */
    OllamaClient(AppProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Send a single chat completion. Returns the raw assistant message content.
     * When {@code jsonMode} is true the model is asked to respond with strict JSON
     * (Ollama's {@code format: "json"} mode).
     */
    public String chat(String model, String systemPrompt, String userPrompt, boolean jsonMode, Duration timeout)
            throws OllamaException {
        return chat(model, systemPrompt, userPrompt, List.of(), jsonMode, timeout);
    }

    public String chat(String model, String systemPrompt, String userPrompt, List<String> base64Images,
                       boolean jsonMode, Duration timeout) throws OllamaException {
        try {
            return chatStreaming(model, systemPrompt, userPrompt, base64Images, jsonMode, timeout);
        } catch (StreamTruncatedException first) {
            log.warn("Ollama stream truncated, retrying once");
            try {
                return chatStreaming(model, systemPrompt, userPrompt, base64Images, jsonMode, timeout);
            } catch (StreamTruncatedException second) {
                log.warn("Ollama stream truncated after retry, falling back to non-streaming request");
                try {
                    return chatBuffered(model, systemPrompt, userPrompt, base64Images, jsonMode, timeout);
                } catch (OllamaException fallback) {
                    fallback.addSuppressed(first);
                    fallback.addSuppressed(second);
                    throw fallback;
                }
            }
        }
    }

    private String chatStreaming(String model, String systemPrompt, String userPrompt, List<String> base64Images,
                                 boolean jsonMode, Duration timeout)
            throws OllamaException {
        // Use streaming mode and accumulate chunks ourselves. Some models (e.g. gemma4)
        // emit "thinking" tokens before the structured output and ignore stream:false,
        // so we always parse NDJSON and concatenate every message.content fragment
        // until done:true.
        // Don't pass keep_alive on the chat call — Ollama otherwise closes the
        // streaming connection early. LocalAiProvider calls unload() after every
        // request to release the model immediately regardless of Ollama's default.
        return runStreaming(buildChatRequest(model, systemPrompt, userPrompt, base64Images, jsonMode, timeout, true));
    }

    private String runStreaming(HttpRequest request) throws OllamaException {
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

    private String chatBuffered(String model, String systemPrompt, String userPrompt, List<String> base64Images,
                                boolean jsonMode, Duration timeout) throws OllamaException {
        return runBuffered(buildChatRequest(model, systemPrompt, userPrompt, base64Images, jsonMode, timeout, false));
    }

    private String runBuffered(HttpRequest request) throws OllamaException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new OllamaException("Ollama unreachable: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Ollama call interrupted", ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new OllamaException("Ollama returned HTTP " + response.statusCode());
        }
        String content = parseBufferedResponse(response.body());
        if (content.isBlank()) {
            throw new OllamaException("Ollama response had no message.content");
        }
        log.debug("Ollama buffered response accumulated {} chars", content.length());
        return content;
    }

    /**
     * Multi-turn, free-text conversation (no JSON mode). {@code messages} is a
     * ready-built list of {@code {role, content}} maps including the system prompt.
     * Used by the chat assistant; reuses the same streaming/buffered fallback path.
     */
    // Structured (workout/meal) JSON generation: large budget so JSON isn't truncated;
    // temperature low enough to keep output parseable.
    private static final Map<String, Object> JSON_OPTIONS = Map.of("num_predict", 4096, "temperature", 0.3);
    // Free-text chat: tighter, more grounded decoding to curb hallucination — lower
    // temperature, nucleus + repeat penalty, and a small budget for short replies.
    private static final Map<String, Object> CHAT_OPTIONS =
            Map.of("num_predict", 512, "temperature", 0.2, "top_p", 0.9, "repeat_penalty", 1.1);

    public String converse(String model, List<Map<String, Object>> messages, Duration timeout) throws OllamaException {
        try {
            return runStreaming(buildMessagesRequest(model, messages, false, timeout, true, CHAT_OPTIONS));
        } catch (StreamTruncatedException first) {
            log.warn("Ollama converse stream truncated, falling back to non-streaming request");
            return runBuffered(buildMessagesRequest(model, messages, false, timeout, false, CHAT_OPTIONS));
        }
    }

    private HttpRequest buildChatRequest(String model, String systemPrompt, String userPrompt, List<String> base64Images,
                                         boolean jsonMode, Duration timeout, boolean stream) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        if (base64Images != null && !base64Images.isEmpty()) {
            userMessage.put("images", base64Images);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(userMessage);
        return buildMessagesRequest(model, messages, jsonMode, timeout, stream, JSON_OPTIONS);
    }

    private HttpRequest buildMessagesRequest(String model, List<Map<String, Object>> messages,
                                             boolean jsonMode, Duration timeout, boolean stream,
                                             Map<String, Object> options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("format", jsonMode ? "json" : "");
        // Disable chain-of-thought: "thinking" models (e.g. gemma4) otherwise spend the
        // whole num_predict budget on reasoning tokens that never land in message.content,
        // yielding an empty reply. This app only wants strict JSON or a short grounded
        // answer, never the reasoning trace. (Harmless for non-thinking models.)
        body.put("think", false);
        body.put("options", options);

        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(properties.ai().ollamaBaseUrl() + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception ex) {
            throw new OllamaException("failed to build Ollama request", ex);
        }
    }

    private String parseBufferedResponse(String body) throws OllamaException {
        StringBuilder content = new StringBuilder();
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode messageContent = response.path("message").path("content");
            if (messageContent.isTextual()) {
                return messageContent.asText();
            }
        } catch (IOException ex) {
            // Some Ollama-compatible servers may still return NDJSON even when
            // stream=false. Fall through and parse line by line.
        }

        boolean sawJson = false;
        for (String line : body.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode chunk = objectMapper.readTree(line);
                sawJson = true;
                JsonNode chunkContent = chunk.path("message").path("content");
                if (chunkContent.isTextual()) {
                    content.append(chunkContent.asText());
                }
            } catch (IOException ex) {
                log.debug("Skipping malformed Ollama buffered chunk: {}", line);
            }
        }
        if (!sawJson) {
            throw new OllamaException("Ollama returned malformed JSON");
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

package com.myhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhealth.config.AppProperties;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 15, 30),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost:11434", "gemma4:e4b", "gemma4:e4b", 60),
                "./uploads");
    }

    @Test
    void chat_retriesStreaming_whenFirstStreamDoesNotEndWithDone() {
        FakeHttpClient httpClient = new FakeHttpClient(List.of(
                Stream.of("{\"message\":{\"content\":\"partial\"},\"done\":false}"),
                Stream.of("{\"message\":{\"content\":\"{\\\"ok\\\":true}\"},\"done\":true}")));
        OllamaClient client = new OllamaClient(properties, objectMapper, httpClient);

        String response = client.chat("gemma4:e4b", "system", "user", true, Duration.ofSeconds(5));

        assertThat(response).isEqualTo("{\"ok\":true}");
        assertThat(httpClient.calls()).isEqualTo(2);
    }

    @Test
    void chat_fallsBackToBufferedRequest_whenStreamingRetryAlsoTruncates() {
        FakeHttpClient httpClient = new FakeHttpClient(List.of(
                Stream.of("{\"message\":{\"content\":\"partial\"},\"done\":false}"),
                Stream.of("{\"message\":{\"content\":\"partial\"},\"done\":false}"),
                "{\"message\":{\"content\":\"{\\\"items\\\":[]}\"},\"done\":true}"));
        OllamaClient client = new OllamaClient(properties, objectMapper, httpClient);

        String response = client.chat("gemma4:e4b", "system", "user", true, Duration.ofSeconds(5));

        assertThat(response).isEqualTo("{\"items\":[]}");
        assertThat(httpClient.calls()).isEqualTo(3);
    }

    private static class FakeHttpClient extends HttpClient {
        private final List<Object> bodies;
        private final AtomicInteger calls = new AtomicInteger();

        FakeHttpClient(List<Object> bodies) {
            this.bodies = bodies;
        }

        int calls() {
            return calls.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            int index = calls.getAndIncrement();
            return new FakeHttpResponse<>((T) bodies.get(index));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(5));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeHttpResponse<T>(T body) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}

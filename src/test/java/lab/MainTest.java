package lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsNonStreamingChatWithoutCredentialsAndReturnsAssistantText() throws Exception {
        URI endpoint = respondWith(200, """
                {"done":true,"done_reason":"stop",
                 "message":{"role":"assistant","content":"第一段\\n第二段：\\\"工具调用\\\""}}
                """);
        String question = "解释 \"工具调用\"\n用中文回答";

        assertEquals("第一段\n第二段：\"工具调用\"",
                Main.ask(CLIENT, endpoint, "test-model", question));

        JsonNode request = new ObjectMapper().readTree(receivedBody.get());
        assertEquals("test-model", request.path("model").asText());
        JsonNode messages = request.path("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.path(0).path("role").asText());
        assertTrue(messages.path(0).path("content").isTextual());
        assertFalse(messages.path(0).path("content").asText().isBlank());
        assertEquals("user", messages.path(1).path("role").asText());
        assertEquals(question, messages.path(1).path("content").asText());
        assertEquals(BooleanNode.FALSE, request.path("stream"));
        assertFalse(request.has("think"));
        assertEquals(2048, request.path("options").path("num_predict").asInt());
        assertEquals("POST", receivedMethod.get());
        assertNull(receivedAuth.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void reportsHttpFailureWithoutEchoingUpstreamBody(int status) throws Exception {
        String privateDetail = "upstream-private-detail";
        URI endpoint = respondWith(status, "{\"error\":\"" + privateDetail + "\"}");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", "你好"));
        assertEquals("HTTP " + status + "：请按 README 排查。", error.getMessage());
        assertFalse(error.getMessage().contains(privateDetail));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidChatResponses")
    void rejectsUnfinishedOrMalformedAssistantResponses(String scenario, String body) throws Exception {
        URI endpoint = respondWith(200, body);
        assertThrows(IllegalStateException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", "你好"));
    }

    private static Stream<Arguments> invalidChatResponses() {
        return Stream.of(
                Arguments.of("unfinished", """
                        {"done":false,"done_reason":"stop","message":{"role":"assistant","content":"部分回答"}}
                        """),
                Arguments.of("truncated", """
                        {"done":true,"done_reason":"length","message":{"role":"assistant","content":"部分回答"}}
                        """),
                Arguments.of("string completion flag", """
                        {"done":"true","done_reason":"stop","message":{"role":"assistant","content":"回答"}}
                        """),
                Arguments.of("missing completion flag", """
                        {"done_reason":"stop","message":{"role":"assistant","content":"回答"}}
                        """),
                Arguments.of("missing completion reason", """
                        {"done":true,"message":{"role":"assistant","content":"回答"}}
                        """),
                Arguments.of("wrong message role", """
                        {"done":true,"done_reason":"stop","message":{"role":"user","content":"回答"}}
                        """),
                Arguments.of("missing message role", """
                        {"done":true,"done_reason":"stop","message":{"content":"回答"}}
                        """),
                Arguments.of("blank text", """
                        {"done":true,"done_reason":"stop","message":{"role":"assistant","content":" \\n\\t"}}
                        """),
                Arguments.of("missing text", """
                        {"done":true,"done_reason":"stop","message":{"role":"assistant"}}
                        """),
                Arguments.of("null text", """
                        {"done":true,"done_reason":"stop","message":{"role":"assistant","content":null}}
                        """),
                Arguments.of("numeric text", """
                        {"done":true,"done_reason":"stop","message":{"role":"assistant","content":42}}
                        """),
                Arguments.of("missing message", """
                        {"done":true,"done_reason":"stop"}
                        """)
        );
    }

    @Test
    void rejectsInvalidJsonInsteadOfPrintingAnEmptyAnswer() throws Exception {
        URI endpoint = respondWith(200, "not-json");
        assertThrows(IOException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", "你好"));
    }

    private URI respondWith(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/chat");
    }
}

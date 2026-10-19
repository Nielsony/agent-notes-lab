package lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {
    private static final String TEST_KEY = "test-key-not-a-real-credential";
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
    void sendsQuestionAsJsonAndCollectsTextAfterNonMessageItems() throws Exception {
        URI endpoint = respondWith(200, """
                {"status":"completed","output":[
                  {"type":"reasoning"},
                  {"type":"message","content":[
                    {"type":"output_text","text":"第一段回答"},
                    {"type":"output_text","text":"第二段回答"}
                  ]}
                ]}
                """);
        String question = "解释 \"工具调用\"\n用中文回答";

        assertEquals("第一段回答\n第二段回答",
                Main.ask(CLIENT, endpoint, "test-model", TEST_KEY, question));

        JsonNode request = new ObjectMapper().readTree(receivedBody.get());
        assertEquals(question, request.path("input").asText());
        assertEquals("test-model", request.path("model").asText());
        assertFalse(request.path("store").asBoolean(true));
        assertEquals("POST", receivedMethod.get());
        assertEquals("Bearer " + TEST_KEY, receivedAuth.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void reportsHttpFailureWithoutEchoingUpstreamBody(int status) throws Exception {
        URI endpoint = respondWith(status, "{\"error\":\"" + TEST_KEY + "\"}");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", TEST_KEY, "你好"));
        assertEquals("HTTP " + status + "：请按 README 排查。", error.getMessage());
        assertFalse(error.getMessage().contains(TEST_KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"failed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"无法回答\"}]}]}"
    })
    void doesNotTreatUnfinishedOrTextlessResponsesAsSuccessfulAnswers(String body) throws Exception {
        URI endpoint = respondWith(200, body);
        assertThrows(IllegalStateException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", TEST_KEY, "你好"));
    }

    @Test
    void rejectsInvalidJsonInsteadOfPrintingAnEmptyAnswer() throws Exception {
        URI endpoint = respondWith(200, "not-json");
        assertThrows(IOException.class,
                () -> Main.ask(CLIENT, endpoint, "test-model", TEST_KEY, "你好"));
    }

    private URI respondWith(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
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
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
    }
}

package lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class Main {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:11434/api/chat");

    public static void main(String[] args) {
        try {
            System.out.print("问题：");
            String question = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                    .readLine();
            if (question == null || question.isBlank()) {
                System.err.println("问题不能为空。");
                System.exit(1);
                return;
            }

            String model = System.getenv("OLLAMA_MODEL");
            if (model == null || model.isBlank()) {
                model = "qwen3:4b";
            }
            System.out.println("调用本地模型：" + model);

            HttpClient client = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(null))
                    .connectTimeout(Duration.ofSeconds(10)).build();
            System.out.println("回答：\n" + ask(client, ENDPOINT, model, question));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("请求被中断。");
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (ConnectException e) {
            System.err.println("无法连接本机 Ollama，请先启动服务（见 README）。");
            System.exit(1);
        } catch (HttpTimeoutException e) {
            System.err.println("本机推理超时，请检查模型加载与服务状态后重试。");
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("读取输入或请求失败，请检查本机 Ollama 服务与响应格式。");
            System.exit(1);
        }
    }

    static String ask(HttpClient client, URI endpoint, String model, String question)
            throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "用中文简洁回答用户的问题。"),
                        Map.of("role", "user", "content", question)),
                "stream", false,
                // Qwen3's thinking and final answer share this budget.
                "options", Map.of("num_predict", 2048)));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + "：请按 README 排查。");
        }

        JsonNode root = JSON.readTree(response.body());
        if (root == null || !root.path("done").isBoolean() || !root.path("done").booleanValue()
                || !"stop".equals(root.path("done_reason").asText())) {
            throw new IllegalStateException("模型响应未正常完成，请检查输出限制和服务状态。");
        }

        JsonNode message = root.path("message");
        JsonNode content = message.path("content");
        if (!"assistant".equals(message.path("role").asText())
                || !content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("响应中没有可显示的文本回答，可能是拒答或不支持的响应类型。");
        }
        return content.asText();
    }
}

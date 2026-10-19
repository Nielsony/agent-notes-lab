package lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Console;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public final class Main {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("请在交互式终端中运行，以便隐藏 API 密钥输入。运行方法见 README。");
            System.exit(1);
            return;
        }

        String question = console.readLine("问题：");
        if (question == null || question.isBlank()) {
            System.err.println("问题不能为空。");
            System.exit(1);
            return;
        }

        char[] enteredKey = console.readPassword("OpenAI API Key（隐藏输入）：");
        if (enteredKey == null) {
            System.err.println("未输入 API 密钥，已结束。");
            System.exit(1);
            return;
        }
        String apiKey = new String(enteredKey);
        Arrays.fill(enteredKey, '\0');
        if (apiKey.isBlank()) {
            System.err.println("API 密钥不能为空。");
            System.exit(1);
            return;
        }

        String model = System.getenv("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = "gpt-4.1-mini";
        }
        System.out.println("调用模型：" + model);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
            System.out.println("回答：\n" + ask(client, ENDPOINT, model, apiKey, question));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("请求被中断。");
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("请求失败：请检查网络、输入和接口响应格式；敏感响应体未输出。");
            System.exit(1);
        }
    }

    static String ask(HttpClient client, URI endpoint, String model, String apiKey, String question)
            throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of(
                "model", model,
                "instructions", "用中文简洁回答用户的问题。",
                "input", question,
                "max_output_tokens", 512,
                "store", false));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + "：请按 README 排查。");
        }

        JsonNode root = JSON.readTree(response.body());
        if (root == null || !"completed".equals(root.path("status").asText())) {
            throw new IllegalStateException("响应未完成，不能作为成功问答。");
        }

        var texts = new ArrayList<String>();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && content.path("text").isTextual()) {
                    texts.add(content.path("text").asText());
                }
            }
        }
        String answer = String.join("\n", texts);
        if (answer.isBlank()) {
            throw new IllegalStateException("响应中没有可显示的文本回答，可能是拒答或不支持的响应类型。");
        }
        return answer;
    }
}

package vn.ainalyzing.api.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local stand-in for Groq Chat Completions: replays scripted replies and records every request. */
final class StubGroq implements AutoCloseable {

    record Reply(int status, String body, long delayMs, Map<String, String> headers) {
    }

    record Request(String body, String authorization) {
    }

    private final HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    final List<Request> requests = new CopyOnWriteArrayList<>();

    StubGroq() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(body, exchange.getRequestHeaders().getFirst("Authorization")));
            Reply r;
            synchronized (replies) {
                r = replies.isEmpty() ? new Reply(500, "{}", 0, Map.of()) : replies.poll();
            }
            try {
                Thread.sleep(r.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            r.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            try {
                exchange.sendResponseHeaders(r.status(), out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            } catch (IOException clientGone) {
                // the client timed out first
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
    }

    StubGroq reply(int status, String body) {
        return reply(status, body, 0, Map.of());
    }

    StubGroq reply(int status, String body, long delayMs, Map<String, String> headers) {
        synchronized (replies) {
            replies.add(new Reply(status, body, delayMs, headers));
        }
        return this;
    }

    StubGroq content(String json) {
        return reply(200, completion(json));
    }

    static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
                + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    static String toolCall(String id, String name, String arguments) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":" + quote(id)
                + ",\"type\":\"function\",\"function\":{\"name\":" + quote(name) + ",\"arguments\":" + quote(arguments)
                + "}}]},\"finish_reason\":\"tool_calls\"}]}";
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

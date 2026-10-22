package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.SearchMapper;
import jakarta.servlet.Filter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 正常端到端走真实生产HTTP/B1；RST和过载只通过条件测试钩子控制时序。 */
class B4HttpIT {
    @TempDir Path directory;
    private static volatile Control control;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void 真HTTP提交真实B1后检索计数详情且snippet按码点() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            for (String text : List.of("ababa", "")) {
                String id = text.isEmpty() ? "empty" : "normal";
                var accepted = json(env, send(env, "POST", "/api/documents", "{\"documentId\":\"" + id
                        + "\",\"text\":\"" + text + "\"}", 202));
                String taskId = accepted.get("taskId").asString();
                until(() -> "SUCCEEDED".equals(env.bean(KnowledgeMapper.class).findTask(taskId).status()));
            }
            var result = json(env, send(env, "POST", "/api/search", "{\"text\":\"ababa\",\"topK\":1}", 200));
            assertEquals(1, result.size(), "无data壳或额外字段");
            assertEquals("normal", result.get("hits").get(0).get("documentId").asString());
            assertEquals(1.0, result.get("hits").get(0).get("score").asDouble(), 1e-12);
            assertEquals(1, env.hits("normal"));
            assertEquals(0, env.hits("empty"));
            send(env, "GET", "/api/documents/normal", null, 200);
            send(env, "GET", "/api/documents", null, 200);
            send(env, "GET", "/api/tasks/" + env.bean(KnowledgeMapper.class).findDocument("normal").taskId(), null, 200);
            assertEquals(1, env.hits("normal"));
            String text = "😀".repeat(199) + "X🚀尾部";
            env.realDocument("unicode", text, "Unicode");
            var snippet = json(env, send(env, "POST", "/api/search", "{\"text\":\"\",\"topK\":100,\"channel\":\"Unicode\"}", 200))
                    .get("hits").get(0).get("snippet").asString();
            assertEquals("😀".repeat(199) + "X", snippet);
            assertEquals(200, snippet.codePointCount(0, snippet.length()));
        }
    }

    @Test
    void 搜索JSON参数渠道Origin与实际chunked字节边界() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            for (String body : List.of("{}", "null", "[]", "{}{}", "{", "{\"text\":null,\"topK\":1}",
                    "{\"text\":4,\"topK\":1}", "{\"text\":\"\",\"topK\":null}", "{\"text\":\"\"}",
                    "{\"text\":\"\",\"topK\":\"1\"}", "{\"text\":\"\",\"topK\":1.0}",
                    "{\"text\":\"\",\"topK\":0}", "{\"text\":\"\",\"topK\":-1}",
                    "{\"text\":\"\",\"topK\":101}", "{\"text\":\"\",\"topK\":999999999999999999999}",
                    "{\"text\":\"\\ud800\",\"topK\":1}", "{\"text\":\"\",\"topK\":1,\"channel\":false}",
                    "{\"text\":\"\",\"topK\":1,\"channel\":\" \\u2007\"}")) {
                var error = json(env, send(env, "POST", "/api/search", body, 400));
                assertTrue(error.get("code").isString());
                assertTrue(error.get("fieldErrors").isArray());
                assertFalse(error.has("stackTrace"));
            }
            send(env, "POST", "/api/search", "{\"text\":\" \",\"topK\":1,\"channel\":null}", 200);
            send(env, "POST", "/api/search", "{\"text\":\"\",\"topK\":1}", 403, "Origin", "https://evil.example");
            send(env, "POST", "/api/search", "{\"text\":\"\",\"topK\":1}", 200,
                    "Origin", "http://127.0.0.1:" + env.port());
            String valid = "{\"text\":\"\",\"topK\":1}";
            assertEquals(200, chunked(env, valid + " ".repeat(262143 - valid.length())));
            assertEquals(200, chunked(env, valid + " ".repeat(262144 - valid.length())));
            assertEquals(413, chunked(env, valid + " ".repeat(262145 - valid.length())));
            assertEquals(0, env.bean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 原始规范化长度和渠道码点边界在真实HTTP校验() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null, "--vector.max-text-code-points=2")) {
            var mapper = env.bean(ObjectMapper.class);
            for (String text : List.of("😀😀😀", "ﬃ", "   ")) {
                send(env, "POST", "/api/search", mapper.writeValueAsString(java.util.Map.of("text", text, "topK", 1)), 400);
            }
            send(env, "POST", "/api/search", mapper.writeValueAsString(java.util.Map.of("text", "😀😀", "topK", 1,
                    "channel", "\u2007" + "😀".repeat(64) + "\u00a0")), 200);
            send(env, "POST", "/api/search", mapper.writeValueAsString(java.util.Map.of("text", "", "topK", 1,
                    "channel", "😀".repeat(65))), 400);
        }
    }

    @Test
    void 默认许可一真实HTTP竞争八请求全部429且准确累计() throws Exception {
        Control observed = new Control("BUSY");
        control = observed;
        try (var env = new B4QaEnvironment(directory, true, true, Hooks.class, "--qa.b4-http-hook=true")) {
            env.realDocument("hot", "ababa", null);
            var executor = Executors.newFixedThreadPool(9);
            try {
                var first = executor.submit(() -> send(env, "POST", "/api/search", "{\"text\":\"qa-block-query\",\"topK\":1}", 200));
                await(observed.cpuEntered);
                var barrier = new CyclicBarrier(8);
                List<java.util.concurrent.Future<?>> competing = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    competing.add(executor.submit(() -> {
                        barrier.await(8, TimeUnit.SECONDS);
                        assertEquals("SEARCH_BUSY", json(env, send(env, "POST", "/api/search",
                                "{\"text\":\"ababa\",\"topK\":1}", 429)).get("code").asString());
                        return null;
                    }));
                }
                for (var result : competing) { result.get(12, TimeUnit.SECONDS); }
                assertEquals(0, env.hits("hot"));
                observed.cpuRelease.countDown();
                first.get(8, TimeUnit.SECONDS);
                assertEquals(1, env.hits("hot"));
                send(env, "POST", "/api/search", "{\"text\":\"ababa\",\"topK\":1}", 200);
                assertEquals(2, env.hits("hot"));
                assertEquals(10, observed.entries.get());
                assertEquals(2, observed.increments.get());
                observed.note("对账attempted=10;committed=2;rejected429=8;hitCount=2");
            } finally {
                observed.cpuRelease.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(12, TimeUnit.SECONDS));
            }
        } finally { control = null; }
    }

    @Test
    void 真实计数已提交后客户端RST不会自动第二次搜索或增量() throws Exception {
        Control observed = new Control("RST");
        control = observed;
        try (var env = new B4QaEnvironment(directory, true, true, Hooks.class, "--qa.b4-http-hook=true")) {
            env.realDocument("network", "ababa", null);
            try (Socket socket = new Socket("127.0.0.1", env.port())) {
                socket.setSoLinger(true, 0);
                String body = "{\"text\":\"ababa\",\"topK\":1}";
                socket.getOutputStream().write(("POST /api/search HTTP/1.1\r\nHost: 127.0.0.1:" + env.port()
                        + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length()
                        + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                await(observed.committed);
                assertEquals(1, env.hits("network"));
                socket.close();
                observed.note("客户端SO_LINGER=0关闭，发送RST；此时计数真实commit结束，响应尚未放行");
                observed.commitRelease.countDown();
            } finally { observed.commitRelease.countDown(); }
            await(observed.completed);
            assertEquals(1, observed.entries.get());
            assertEquals(1, observed.increments.get());
            send(env, "GET", "/api/documents/network", null, 200);
            assertEquals(1, env.hits("network"));
            observed.note("对账POST入口=1;计数SQL=1;持久增量=1;客户端未接收响应;无自动重放");
        } finally { observed.commitRelease.countDown(); control = null; }
    }

    private HttpResponse<String> send(B4QaEnvironment env, String method, String path, String body, int status, String... headers)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + path)).timeout(Duration.ofSeconds(8));
        if (body != null) { builder.header("Content-Type", "application/json"); }
        for (int i = 0; i < headers.length; i += 2) { builder.header(headers[i], headers[i + 1]); }
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response;
    }

    private static JsonNode json(B4QaEnvironment env, HttpResponse<String> response) {
        return env.bean(ObjectMapper.class).readTree(response.body());
    }

    /** 直接构造无Content-Length的chunked线报文，排除客户端自动加长度头的干扰。 */
    private static int chunked(B4QaEnvironment env, String body) throws Exception {
        try (var socket = new Socket("127.0.0.1", env.port())) {
            socket.setSoTimeout(8000);
            var output = socket.getOutputStream();
            output.write(("POST /api/search HTTP/1.1\r\nHost: 127.0.0.1:" + env.port()
                    + "\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            for (int offset = 0; offset < bytes.length; offset += 4096) {
                int count = Math.min(4096, bytes.length - offset);
                output.write((Integer.toHexString(count) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes, offset, count);
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 "), response);
            return Integer.parseInt(response.substring(9, 12));
        }
    }

    static final class Control {
        final String mode;
        final CountDownLatch cpuEntered = new CountDownLatch(1);
        final CountDownLatch cpuRelease = new CountDownLatch(1);
        final CountDownLatch committed = new CountDownLatch(1);
        final CountDownLatch commitRelease = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicInteger entries = new AtomicInteger();
        final AtomicInteger increments = new AtomicInteger();
        final AtomicBoolean firstCommit = new AtomicBoolean(true);
        final Path evidence;

        Control(String mode) throws Exception {
            this.mode = mode;
            Files.createDirectories(Path.of("target/b4-evidence"));
            evidence = Path.of("target/b4-evidence", mode + "-" + UUID.randomUUID() + ".txt");
            note("runId=" + evidence.getFileName() + ";sourceSHA=" + B4Evidence.sourceSha()
                    + ";tests=本轮测试工作树;thread=" + Thread.currentThread().getName());
        }

        synchronized void note(String text) {
            try { Files.writeString(evidence, text + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "qa.b4-http-hook", havingValue = "true")
    static class Hooks {
        @Bean
        Filter qaSearchEvents() {
            Control observed = control;
            return (request, response, chain) -> {
                boolean search = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/api/search");
                if (search) { observed.entries.incrementAndGet(); observed.note("POST进入thread=" + Thread.currentThread().getName()); }
                try { chain.doFilter(request, response); }
                finally { if (search) { observed.note("HTTP链返回"); observed.completed.countDown(); } }
            };
        }

        @Bean
        static BeanPostProcessor qaSearchTiming() {
            Control observed = control;
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof TextEmbedder embedder && observed.mode.equals("BUSY")) {
                        return new TextEmbedder() {
                            @Override
                            public float[] embed(String text) {
                                float[] result = embedder.embed(text);
                                if (text.equals("qa-block-query")) {
                                    observed.cpuEntered.countDown();
                                    try { await(observed.cpuRelease); }
                                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                                }
                                return result;
                            }
                            @Override public String version() { return embedder.version(); }
                        };
                    }
                    if (bean instanceof SearchMapper actual) {
                        return Proxy.newProxyInstance(SearchMapper.class.getClassLoader(), new Class<?>[]{SearchMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("incrementHit")) {
                                        observed.increments.incrementAndGet(); observed.note("计数SQL id=" + args[0]);
                                    }
                                    try { return method.invoke(actual, args); }
                                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                                });
                    }
                    if (bean instanceof PlatformTransactionManager manager && observed.mode.equals("RST")) {
                        return new B2QaEnvironment.Manager(manager) {
                            @Override
                            public void commit(TransactionStatus status) {
                                super.commit(status);
                                if (observed.increments.get() > 0 && observed.firstCommit.compareAndSet(true, false)) {
                                    observed.note("计数真实COMMIT已返回，响应前暂停");
                                    observed.committed.countDown();
                                    try { await(observed.commitRelease); }
                                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                                }
                            }
                        };
                    }
                    return bean;
                }
            };
        }
    }
}

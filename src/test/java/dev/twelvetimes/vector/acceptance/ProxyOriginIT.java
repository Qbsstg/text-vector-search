package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 隔离复现代理组合；观察器只在测试源码注册，不新增生产诊断端点。 */
class ProxyOriginIT {
    private static final String HOST = "text-vector-search.lemoncharger.com";
    private static final String LOOPBACK_PROXIES = "127\\.0\\.0\\.1|::1|0:0:0:0:0:0:0:1";
    private static final String FORWARDED = "X-Forwarded-Proto: https\r\nX-Forwarded-Host: " + HOST
            + "\r\nX-Forwarded-Port: 443\r\nX-Forwarded-For: 198.51.100.20\r\n";
    @TempDir Path directory;

    record Seen(String scheme, String host, int port, String remoteAddress) {
    }

    @Test
    void 默认配置不识别HTTPS转发头而本地HTTP同源正常() throws Exception {
        try (var context = start("default", false)) {
            assertEquals(202, request(context, "http", "plain", "", null));
            assertSeen(context, "http", HOST, 80);
            assertEquals(403, request(context, "https", "https-default", FORWARDED, null));
            assertSeen(context, "http", HOST, 80);
            assertEquals(1, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 可信回环原生配置识别三元组且仍拒绝跨源和恶意Forwarded() throws Exception {
        try (var context = start("native", true)) {
            assertEquals("native", context.getEnvironment().getProperty("server.forward-headers-strategy"));
            assertEquals(LOOPBACK_PROXIES, context.getEnvironment().getProperty("server.tomcat.remoteip.internal-proxies"));
            assertEquals(202, request(context, "https", "native-ok", FORWARDED, null));
            assertSeen(context, "https", HOST, 443);
            // 原生Valve只取显式配置的X-Forwarded头，不能改成框架无条件信任Forwarded模式。
            assertEquals(202, request(context, "https", "conflicting-forwarded", FORWARDED
                    + "Forwarded: for=203.0.113.55;proto=http;host=evil.example:81\r\n", null));
            assertSeen(context, "https", HOST, 443);
            assertEquals(403, requestWithOrigin(context, "https://evil.example", "cross-origin", FORWARDED, null));
            assertSeen(context, "https", HOST, 443);
            assertEquals(403, request(context, "https", "forwarded-alone",
                    "Forwarded: for=203.0.113.55;proto=https;host=" + HOST + "\r\n", null));
            assertSeen(context, "http", HOST, 80);
            assertEquals(2, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 非信任地址不能借转发头伪造有效HTTPS来源() throws Exception {
        try (var context = start("untrusted", true)) {
            try {
                assertEquals(403, request(context, "https", "untrusted", FORWARDED, "127.0.0.2"));
                assertSeen(context, "http", HOST, 80);
                assertEquals("127.0.0.2", context.getBean(Observations.class).last.get().remoteAddress());
                System.out.println("代理边界证据：真实socket源127.0.0.2不受信任，未应用转发头");
            } catch (BindException unavailable) {
                // macOS可能未配置127.0.0.2别名；不将未执行的真实网络边界写成通过。
                System.out.println("代理边界证据：平台无法绑定127.0.0.2，改用真实RemoteIpValve组件验证；非信任来源socket未执行");
                assertUntrustedValve();
            }
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    private ConfigurableApplicationContext start(String name, boolean trustedProxy) {
        var args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--server.port=0", "--server.address=127.0.0.1", "--spring.datasource.url=jdbc:h2:file:"
                        + directory.resolve(name + "/knowledge").toAbsolutePath() + ";WRITE_DELAY=0",
                "--spring.datasource.username=sa", "--spring.datasource.password="));
        if (trustedProxy) {
            // 直接加载主控已提交的拟发布资源，不用CLI属性替代资源本身的验证。
            args.add("--spring.profiles.active=proxy");
        }
        return new SpringApplication(B2CompositionTestApplication.class, ObserverConfiguration.class)
                .run(args.toArray(String[]::new));
    }

    private static int request(ConfigurableApplicationContext context, String scheme, String id, String headers,
                                String localAddress) throws Exception {
        return requestWithOrigin(context, scheme + "://" + HOST, id, headers, localAddress);
    }

    /** 原始HTTP报文能精确指定Host和绑定源IP，不依赖HTTP客户端受限头开关。 */
    private static int requestWithOrigin(ConfigurableApplicationContext context, String origin, String id, String headers,
                                          String localAddress) throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        String body = "{\"documentId\":\"" + id + "\",\"text\":\"\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (var socket = new Socket()) {
            if (localAddress != null) {
                socket.bind(new InetSocketAddress(localAddress, 0));
            }
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(8000);
            var output = socket.getOutputStream();
            output.write(("POST /api/documents HTTP/1.1\r\nHost: " + HOST + "\r\nOrigin: " + origin
                    + "\r\n" + headers + "Content-Type: application/json\r\nContent-Length: " + bytes.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 "), response);
            int status = Integer.parseInt(response.substring(9, 12));
            if (status == 403) {
                assertTrue(response.contains("ORIGIN_NOT_ALLOWED"), response);
            }
            return status;
        }
    }

    private static void assertSeen(ConfigurableApplicationContext context, String scheme, String host, int port) {
        Seen seen = context.getBean(Observations.class).last.get();
        assertNotNull(seen);
        assertEquals(scheme, seen.scheme());
        assertEquals(host, seen.host());
        assertEquals(port, seen.port());
        System.out.println("代理三元组证据：" + seen);
    }

    /** 直接调用Tomcat生产Valve，确认非信任地址分支不处理转发头；不是公网部署验证。 */
    private static void assertUntrustedValve() throws Exception {
        var coyote = new org.apache.coyote.Request();
        coyote.scheme().setString("http");
        coyote.serverName().setString(HOST);
        coyote.setServerPort(80);
        coyote.remoteAddr().setString("127.0.0.2");
        coyote.getMimeHeaders().addValue("X-Forwarded-Proto").setString("https");
        coyote.getMimeHeaders().addValue("X-Forwarded-Host").setString("evil.example");
        coyote.getMimeHeaders().addValue("X-Forwarded-Port").setString("443");
        coyote.getMimeHeaders().addValue("X-Forwarded-For").setString("198.51.100.20");
        var request = new Request(new Connector(), coyote);
        request.setRemoteAddr("127.0.0.2");
        var response = new Response(new org.apache.coyote.Response());
        var valve = new RemoteIpValve();
        valve.setInternalProxies(LOOPBACK_PROXIES);
        valve.setProtocolHeader("X-Forwarded-Proto");
        valve.setHostHeader("X-Forwarded-Host");
        valve.setPortHeader("X-Forwarded-Port");
        valve.setRemoteIpHeader("X-Forwarded-For");
        AtomicReference<Seen> effective = new AtomicReference<>();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request current, Response ignored) {
                effective.set(new Seen(current.getScheme(), current.getServerName(), current.getServerPort(),
                        current.getRemoteAddr()));
            }
        });
        valve.invoke(request, response);
        assertEquals(new Seen("http", HOST, 80, "127.0.0.2"), effective.get());
        assertFalse(request.isSecure());
    }

    static final class Observations {
        final AtomicReference<Seen> last = new AtomicReference<>();
    }

    /** @TestConfiguration不会被其他生产启动测试的组件扫描自动导入。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class ObserverConfiguration {
        @Bean
        Observations proxyObservations() {
            return new Observations();
        }

        @Bean
        Filter proxyObservationFilter(Observations observations) {
            return new ObserverFilter(observations);
        }
    }

    /** 顺序早于Origin过滤器，连被403拒绝的请求也能观察原生Valve处理后的三元组。 */
    static final class ObserverFilter implements Filter, Ordered {
        private final Observations observations;

        ObserverFilter(Observations observations) {
            this.observations = observations;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 10;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            var http = (HttpServletRequest) request;
            observations.last.set(new Seen(http.getScheme(), http.getServerName(), http.getServerPort(), http.getRemoteAddr()));
            chain.doFilter(request, response);
        }
    }
}

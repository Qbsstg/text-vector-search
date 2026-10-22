package dev.twelvetimes.vector.api.filter;

import dev.twelvetimes.vector.api.contract.ApiModels.ErrorResponse;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 在MVC前限制实际读取字节，兼容chunked；浏览器写入必须同源且不开放CORS。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class ApiRequestFilter extends OncePerRequestFilter {
    private final int maximumBytes;
    private final ObjectMapper mapper;

    public ApiRequestFilter(VectorProperties properties, ObjectMapper mapper) {
        maximumBytes = properties.maxRequestBytes();
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            boolean write = !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
            if (write) { checkOrigin(request); }
            if (request.getContentLengthLong() > maximumBytes) { throw tooLarge(); }
            // 只多读一个字节即可判超限，不能对chunked调用无界readAllBytes。
            byte[] bytes = request.getInputStream().readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) { throw tooLarge(); }
            if (request.getMethod().equals("PUT") && bytes.length > 0) {
                throw BusinessException.invalid("body", "失效操作不接受请求体");
            }
            if (request.getMethod().equals("POST")) {
                try {
                    MediaType type = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
                    if (!"application".equalsIgnoreCase(type.getType()) || !"json".equalsIgnoreCase(type.getSubtype())) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException exception) {
                    throw BusinessException.invalid("Content-Type", "写入正文必须使用application/json");
                }
            }
            chain.doFilter(new CachedBody(request, bytes), response);
        } catch (BusinessException exception) {
            response.setStatus(exception.status());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getOutputStream().write(mapper.writeValueAsBytes(new ErrorResponse(
                    exception.code(), exception.getMessage(), exception.fieldErrors())));
        }
    }

    private static BusinessException tooLarge() {
        return new BusinessException(413, "REQUEST_TOO_LARGE", "请求体超过字节上限");
    }

    private static void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) { return; }
        try {
            URI uri = URI.create(origin);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
            if (uri.getHost() == null || uri.getScheme() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !uri.getScheme().equalsIgnoreCase(request.getScheme())
                    || !uri.getHost().equalsIgnoreCase(request.getServerName()) || port != request.getServerPort()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(403, "ORIGIN_NOT_ALLOWED", "浏览器写入来源不被允许");
        }
    }

    private static final class CachedBody extends HttpServletRequestWrapper {
        private final byte[] bytes;
        CachedBody(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("此接口仅支持同步读取");
                }
            };
        }
    }
}

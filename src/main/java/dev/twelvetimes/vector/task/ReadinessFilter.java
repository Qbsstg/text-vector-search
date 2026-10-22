package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.contract.ApiModels.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 恢复/停止拒绝全部业务；降级保留GET诊断，但不允许写入。健康不经过此过滤器。 */
public final class ReadinessFilter extends OncePerRequestFilter implements Ordered {
    private final TaskReadiness readiness;
    private final ObjectMapper mapper;

    public ReadinessFilter(TaskReadiness readiness, ObjectMapper mapper) { this.readiness = readiness; this.mapper = mapper; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TaskReadiness.State state = readiness.state();
        if (state == TaskReadiness.State.READY || (state == TaskReadiness.State.DEGRADED && request.getMethod().equals("GET"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(503);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(mapper.writeValueAsBytes(new ErrorResponse("SERVICE_NOT_READY", "任务服务尚未就绪或正在停止", List.of())));
    }
}

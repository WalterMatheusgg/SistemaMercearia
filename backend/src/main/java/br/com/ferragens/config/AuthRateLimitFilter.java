package br.com.ferragens.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/** Bounded per-process protection; multi-instance deployments also need a shared proxy limiter. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}

    private final Map<String, Window> windows = new HashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (request.getMethod().equals("POST")
                && Set.of(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password/request",
                                "/api/auth/password/reset")
                        .contains(path)) {
            String key = request.getRemoteAddr() + ":" + path;
            long now = System.currentTimeMillis();
            boolean blocked;
            synchronized (windows) {
                windows.entrySet().removeIf(e -> now - e.getValue().start() > 60000);
                Window old = windows.get(key);
                int count = old == null ? 1 : old.count() + 1;
                blocked = count > 20 || old == null && windows.size() >= 10000;
                if (!blocked) windows.put(key, new Window(old == null ? now : old.start(), count));
            }
            if (blocked) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/problem+json");
                response.getWriter()
                        .write(
                                "{\"title\":\"Muitas"
                                    + " tentativas\",\"status\":429,\"detail\":\"Aguarde um minuto"
                                    + " antes de tentar novamente.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

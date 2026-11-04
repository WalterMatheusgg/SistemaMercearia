package br.com.ferragens.config;

import br.com.ferragens.commerce.Db;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Account changes invalidate existing sessions on their next request. */
public class SessionValidationFilter extends OncePerRequestFilter {
    private final Db db;

    public SessionValidationFilter(Db db) {
        this.db = db;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var session = request.getSession(false);
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            var users =
                    db.rows(
                            "SELECT enabled,auth_revision FROM app_user WHERE username=?",
                            auth.getName());
            Object revision = session == null ? null : session.getAttribute("accountRevision");
            if (users.isEmpty()
                    || !Boolean.TRUE.equals(users.getFirst().get("enabled"))
                    || revision == null
                    || ((Number) revision).longValue()
                            != ((Number) users.getFirst().get("authRevision")).longValue()) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                if (!request.getRequestURI().startsWith("/api/")) {
                    chain.doFilter(request, response);
                    return;
                }
                response.setStatus(401);
                response.setContentType("application/problem+json");
                response.getWriter()
                        .write(
                                "{\"type\":\"about:blank\",\"title\":\"Sessão"
                                        + " encerrada\",\"status\":401,\"detail\":\"Sua conta foi"
                                        + " alterada. Entre novamente.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

package br.com.ferragens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Observação de erros internos HTTP 5xx. Registra somente data, método, endpoint
 * e status; nunca salva senha, body, cookies, URL com query string ou stack trace.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class InternalErrorMonitor extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    public InternalErrorMonitor(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        boolean failed=false;
        try {
            chain.doFilter(request,response);
        } catch (IOException | ServletException | RuntimeException error) {
            failed=true;
            throw error;
        } finally {
            if ((failed || response.getStatus() >= 500) && request.getRequestURI().startsWith("/api/")) {
                try {
                    String endpoint=request.getRequestURI()
                        .replaceAll("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}","{id}")
                        .replaceAll("/[0-9]+(?=/|$)","/{n}")
                        .replaceAll("[\\p{Cntrl}]","");
                    if (endpoint.length()>200) endpoint=endpoint.substring(0,200);
                    jdbc.update("INSERT INTO internal_error_event(id,request_method,endpoint,http_status) "
                            + "VALUES(?,?,?,?)",UUID.randomUUID(),request.getMethod(),
                            endpoint,failed?500:response.getStatus());
                } catch (Exception ignored) {
                    // Se o banco também falhou, não escondemos o erro original nem
                    // fazemos uma segunda escrita que possa causar cascata.
                }
            }
        }
    }
}

package br.com.ferragens.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Used by the packaged Docker application; Angular dev-server handles these paths in development.
 */
@Controller
public class SpaController {
    @GetMapping({
        "/",
        "/store",
        "/cart",
        "/login",
        "/register",
        "/forgot-password",
        "/reset-password",
        "/verify-email",
        "/account",
        "/admin",
        "/admin/{page}"
    })
    public String index() {
        return "forward:/index.html";
    }
}

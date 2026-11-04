package br.com.ferragens.auth;

import java.util.List;

public record CurrentUserResponse(String username, List<String> roles) {
}
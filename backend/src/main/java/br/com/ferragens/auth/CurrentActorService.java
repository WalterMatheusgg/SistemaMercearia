package br.com.ferragens.auth;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentActorService {
    private final UserAccountRepository users;

    public CurrentActorService(UserAccountRepository users) { this.users = users; }

    public Actor require(Authentication authentication) {
        if (authentication == null) throw new InsufficientAuthenticationException("Autenticação necessária.");
        var user = users.findByUsername(authentication.getName())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new InsufficientAuthenticationException("Sessão inválida."));
        return new Actor(user.getId(), user.getStoreId(), user.getUsername());
    }

    public record Actor(UUID userId, UUID storeId, String username) { }
}

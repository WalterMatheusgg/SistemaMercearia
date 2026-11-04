package br.com.ferragens.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountsService accounts;private final CurrentActorService actors;
    public AccountController(AccountsService accounts,CurrentActorService actors){this.accounts=accounts;this.actors=actors;}
    public record Request(@NotBlank @Size(max=254)String username){}
    public record Token(@NotBlank @Size(max=200)String token){}
    @PostMapping("/auth/register")public Map<String,Object> register(@Valid @RequestBody AccountsService.Register body){return accounts.register(body);}
    @PostMapping("/auth/password/request")public Map<String,String> request(@Valid @RequestBody Request body){accounts.requestReset(body.username());return Map.of("message","Se houver conta ativa com e-mail cadastrado, enviaremos as instruções.");}
    @PostMapping("/auth/password/reset")public Map<String,String> reset(@Valid @RequestBody AccountsService.Reset body){accounts.reset(body);return Map.of("message","Senha atualizada. Entre novamente.");}
    @PostMapping("/auth/email/verify")public Map<String,String> verify(@Valid @RequestBody Token body){accounts.verify(body.token());return Map.of("message","E-mail verificado.");}
    @PostMapping("/account/email/request-verification")public Map<String,String> verification(Authentication auth){accounts.requestVerification(actors.require(auth));return Map.of("message","Confira seu e-mail; no ambiente local a mensagem fica no Mailpit.");}
    @GetMapping("/account/profile")@PreAuthorize("hasRole('CUSTOMER')")public Map<String,Object> profile(Authentication auth){return accounts.profile(actors.require(auth));}
    @PutMapping("/account/profile")@PreAuthorize("hasRole('CUSTOMER')")public Map<String,Object> profile(Authentication auth,@Valid @RequestBody AccountsService.Profile body){return accounts.profile(actors.require(auth),body);}
    @GetMapping("/admin/users")@PreAuthorize("hasAnyRole('OWNER','MANAGER')")public List<Map<String,Object>> users(Authentication auth){return accounts.users(actors.require(auth));}
    @PostMapping("/admin/users")@PreAuthorize("hasAnyRole('OWNER','MANAGER')")public Map<String,Object> create(Authentication auth,@RequestHeader("Idempotency-Key")UUID key,@Valid @RequestBody AccountsService.UserInput body){return accounts.saveUser(actors.require(auth),key,null,body,has(auth,"OWNER"));}
    @PutMapping("/admin/users/{id}")@PreAuthorize("hasAnyRole('OWNER','MANAGER')")public Map<String,Object> update(Authentication auth,@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@Valid @RequestBody AccountsService.UserInput body){return accounts.saveUser(actors.require(auth),key,id,body,has(auth,"OWNER"));}
    @GetMapping("/admin/notifications")@PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")public Map<String,Object> prefs(Authentication auth){return accounts.preferences(actors.require(auth));}
    @PutMapping("/admin/notifications")@PreAuthorize("hasAnyRole('OWNER','MANAGER','STOCKKEEPER')")public Map<String,Object> prefs(Authentication auth,@Valid @RequestBody AccountsService.Preferences body){return accounts.preferences(actors.require(auth),body,has(auth,"OWNER")||has(auth,"MANAGER"));}
    private boolean has(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
}

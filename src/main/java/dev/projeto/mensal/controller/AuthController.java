package dev.projeto.mensal.controller;

import dev.projeto.mensal.entity.Login;
import dev.projeto.mensal.entity.Usuario;
import dev.projeto.mensal.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://frontend.dua.dev.br")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UsuarioService usuarioService;

    @Value("${app.role.prefix}")
    private String rolePrefix;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @PostMapping("/sync")
    public ResponseEntity<Usuario> sync(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaim("email");
        String nome = jwt.getClaim("preferred_username");

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        List<String> roles = (List<String>) realmAccess.get("roles");

        String role = roles.stream()
                .filter(r -> r.startsWith(rolePrefix))
                .findFirst()
                .orElse(rolePrefix + "_user");

        Usuario usuario;
        try {
            usuario = usuarioService.buscarPorKeycloakId(keycloakId);
        } catch (Exception e) {
            usuario = new Usuario();
            usuario.setKeycloakId(keycloakId);
            usuario.setEmail(email);
            usuario.setUsername(nome);
            usuario.setRole(role);
            usuario = usuarioService.salvar(usuario);
        }

        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Login login) {
        String keycloakUrl = issuerUri + "/protocol/openid-connect/token";
        log.info("Tentativa de login: username={}", login.getUsername());

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", login.getUsername());
        body.add("password", login.getPassword());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(keycloakUrl, entity, Map.class);
            log.info("Login bem-sucedido para: {}", login.getUsername());
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Keycloak rejeitou login [{}]: status={}, body={}",
                    login.getUsername(), e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar Keycloak para [{}]: {}", login.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao autenticar");
        }
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrar(@RequestBody Usuario usuario) {
        RestTemplate restTemplate = new RestTemplate();


        String adminToken = getAdminToken();

        // cria no Keycloak
        HttpHeaders headersKeycloak = new HttpHeaders();
        headersKeycloak.setContentType(MediaType.APPLICATION_JSON);
        headersKeycloak.setBearerAuth(adminToken);

        Map<String, Object> keycloakUser = new HashMap<>();
        keycloakUser.put("username", usuario.getUsername());
        keycloakUser.put("email", usuario.getEmail());
        keycloakUser.put("enabled", true);
        keycloakUser.put("emailVerified", true);
        keycloakUser.put("credentials", List.of(Map.of(
                "type", "password",
                "value", usuario.getPassword(),
                "temporary", false
        )));

        ResponseEntity<Void> keycloakResponse;
        try {

            String urlCadastroKeycloak = serverUrl + "/admin/realms/" + realm + "/users";
            keycloakResponse = restTemplate.postForEntity(
                    urlCadastroKeycloak,
                    new HttpEntity<>(keycloakUser, headersKeycloak), Void.class
            );
        } catch (HttpClientErrorException e) {
            log.error("Erro Keycloak ao cadastrar: {}", e.getResponseBodyAsString());
            return ResponseEntity.badRequest().body(e.getResponseBodyAsString());
        }

        // pega o keycloakId pelo header Location
        String location = keycloakResponse.getHeaders().getFirst("Location");
        if (location == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível obter a confirmação do Keycloak.");
        }
        String keycloakId = location.substring(location.lastIndexOf("/") + 1);

        // salva no banco
        usuario.setKeycloakId(keycloakId);
        usuario.setRole(rolePrefix + "_user");
        usuario.setPassword(null); // Segurança: nunca salvar senha pura no banco local

        usuarioService.salvar(usuario);

        return ResponseEntity.ok("Usuário cadastrado com sucesso!");
    }

    private String getAdminToken() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                issuerUri + "/protocol/openid-connect/token", entity, Map.class
        );

        return (String) response.getBody().get("access_token");
    }
}
package dev.projeto.mensal.controller;

import dev.projeto.mensal.entity.Login;
import dev.projeto.mensal.entity.Usuario;
import dev.projeto.mensal.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    private final UsuarioService usuarioService;

    @Value("${app.role.prefix}")
    private String rolePrefix;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

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

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "debora");
        body.add("client_secret", "yZmrlIJYBht8weP0Pqq3JgyDwoGfwEy7");
        body.add("username", login.getUsername());
        body.add("password", login.getPassword());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                keycloakUrl, entity, Map.class
        );

        return ResponseEntity.ok(response.getBody());
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrar(@RequestBody Usuario usuario) {

        // pega token admin
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headersToken = new HttpHeaders();
        headersToken.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> bodyToken = new LinkedMultiValueMap<>();
        bodyToken.add("grant_type", "client_credentials");
        bodyToken.add("client_id", clientId);
        bodyToken.add("client_secret", clientSecret);

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                issuerUri + "/protocol/openid-connect/token",
                new HttpEntity<>(bodyToken, headersToken), Map.class
        );

        String adminToken = (String) tokenResponse.getBody().get("access_token");

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
            keycloakResponse = restTemplate.postForEntity(
                    "http://10.35.228.150:5001/admin/realms/bia-mensal/users",
                    new HttpEntity<>(keycloakUser, headersKeycloak), Void.class
            );
        } catch (HttpClientErrorException e) {
            System.out.println("Erro Keycloak: " + e.getResponseBodyAsString());
            return ResponseEntity.badRequest().body(e.getResponseBodyAsString());
        }

        // pega o keycloakId pelo header Location
        String location = keycloakResponse.getHeaders().getFirst("Location");
        String keycloakId = location.substring(location.lastIndexOf("/") + 1);

        // salva no banco
        usuario.setKeycloakId(keycloakId);
        usuario.setRole(rolePrefix + "_user");
        usuario.setPassword(null);

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
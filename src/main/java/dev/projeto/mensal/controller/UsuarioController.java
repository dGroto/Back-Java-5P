package dev.projeto.mensal.controller;

import dev.projeto.mensal.entity.Usuario;
import dev.projeto.mensal.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "http://localhost:4200")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    // LISTAR TODOS
    @GetMapping("/findAll")
    public List<Usuario> findAll() {
        return usuarioService.findAll();
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<Usuario> cadastrar(@RequestBody Usuario usuario) {
        Usuario salvo = usuarioService.salvar(usuario);
        return ResponseEntity.ok(salvo);
    }

    // DELETAR (protegido por role do Keycloak)
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        usuarioService.deletar(id);
        return ResponseEntity.ok("Usuário deletado com sucesso!");
    }

    // USUÁRIO LOGADO (dados do token)
    @GetMapping("/me")
    public ResponseEntity<?> usuarioLogado(@AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaim("email");
        String username = jwt.getClaim("preferred_username");

        return ResponseEntity.ok(
                "ID: " + keycloakId +
                        " | Email: " + email +
                        " | Username: " + username
        );
    }

    // BUSCAR USUÁRIO NO BANCO PELO TOKEN
    @GetMapping("/me-db")
    public ResponseEntity<Usuario> getUsuarioBanco(@AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        Usuario usuario = usuarioService.buscarPorKeycloakId(keycloakId);

        return ResponseEntity.ok(usuario);
    }
}
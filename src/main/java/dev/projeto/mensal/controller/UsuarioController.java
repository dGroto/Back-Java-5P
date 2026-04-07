package dev.projeto.mensal.controller;

import dev.projeto.mensal.entity.Usuario;
import dev.projeto.mensal.repository.UsuarioRepository;
import dev.projeto.mensal.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "http://localhost:4200")
public class UsuarioController {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    //LISTAR TODOS
    @GetMapping("findAll")
    public List<Usuario> findAll() {
        return usuarioService.findAll();
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try {
            usuarioService.deletar(id);
            return new ResponseEntity<>("Usuário deletado com sucesso!", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Erro ao deletar usuário", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
package dev.projeto.mensal.service;

import dev.projeto.mensal.config.JwtServiceGenerator;
import dev.projeto.mensal.entity.Login;
import dev.projeto.mensal.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import dev.projeto.mensal.repository.UsuarioRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {


    @Autowired
    private final UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    private JwtServiceGenerator jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }


    public void deletar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new RuntimeException("Usuário não encontrado");
        }

        usuarioRepository.deleteById(id);
    }

    public String cadastrar(Usuario usuario) {

        // CRIPTOGRAFANDO A SENHA
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));

        if (usuario.getRole() == null) {
            usuario.setRole("USER");
        }

        //SALVANDO O OBJETO
        usuarioRepository.save(usuario);

        return "Usuário cadastrado com sucesso!";
    }



}

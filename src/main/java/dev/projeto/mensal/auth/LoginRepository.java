package dev.projeto.mensal.auth;

import java.util.Optional;

import dev.projeto.mensal.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;


public interface LoginRepository extends JpaRepository<Usuario, Long>{

	public Optional<Usuario> findByUsername(String login);
	
}

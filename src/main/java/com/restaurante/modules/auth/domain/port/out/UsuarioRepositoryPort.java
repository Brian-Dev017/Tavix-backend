package com.restaurante.modules.auth.domain.port.out;

import com.restaurante.modules.auth.domain.model.Usuario;
import java.util.Optional;

public interface UsuarioRepositoryPort {
    Optional<Usuario> findByUsuario(String usuario);
    Optional<Usuario> findById(Long id);
}

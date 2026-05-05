package com.restaurante.modules.auth.infrastructure.persistence;

import com.restaurante.modules.auth.domain.model.Rol;
import com.restaurante.modules.auth.domain.model.Usuario;
import com.restaurante.modules.auth.domain.port.out.UsuarioRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUsuarioRepository implements UsuarioRepositoryPort {

    private final UsuarioJpaRepo jpaRepo;

    public JpaUsuarioRepository(UsuarioJpaRepo jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public Optional<Usuario> findByUsuario(String usuario) {
        return jpaRepo.findByUsuario(usuario).map(this::toModel);
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return jpaRepo.findById(id).map(this::toModel);
    }

    private Usuario toModel(UsuarioEntity e) {
        return new Usuario(e.getId(), e.getNombre(), e.getApellido(),
                e.getUsuario(), e.getContrasenaHash(),
                Rol.valueOf(e.getRolId()), e.isActivo());
    }
}

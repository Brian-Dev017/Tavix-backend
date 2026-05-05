package com.restaurante.modules.auth.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String apellido;

    @Column(nullable = false, unique = true, length = 30)
    private String usuario;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "rol_id", nullable = false, length = 2)
    private String rolId;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getUsuario() { return usuario; }
    public String getContrasenaHash() { return contrasenaHash; }
    public String getRolId() { return rolId; }
    public boolean isActivo() { return activo; }

    public void setId(Long id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }
    public void setRolId(String rolId) { this.rolId = rolId; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

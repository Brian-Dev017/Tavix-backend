package com.restaurante.modules.auth.domain.model;

public class Usuario {
    private Long id;
    private String nombre;
    private String apellido;
    private String usuario;
    private String contrasenaHash;
    private Rol rol;
    private boolean activo;

    public Usuario() {}

    public Usuario(Long id, String nombre, String apellido, String usuario,
                   String contrasenaHash, Rol rol, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.usuario = usuario;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
        this.activo = activo;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getUsuario() { return usuario; }
    public String getContrasenaHash() { return contrasenaHash; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
}

package com.restaurante.modules.auth.domain.model;

public enum Rol {
    AD, ME, CO, CA;

    public String getNombre() {
        return switch (this) {
            case AD -> "Administrador";
            case ME -> "Mesero";
            case CO -> "Cocina";
            case CA -> "Caja";
        };
    }
}

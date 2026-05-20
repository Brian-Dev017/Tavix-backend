-- =============================================================
--  Tavix Backend — Script inicial de base de datos
--  Base de datos : restaurante
--  Motor         : MySQL 8
--  Generado      : 2026-05-05
-- =============================================================

-- -------------------------------------------------------------
-- AUTH
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS usuario (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(50)   NOT NULL,
    apellido       VARCHAR(50)   NOT NULL,
    usuario        VARCHAR(30)   NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL,
    rol_id         VARCHAR(2)    NOT NULL COMMENT 'AD=Admin, ME=Mesero, CO=Cocinero, CA=Cajero',
    activo         TINYINT(1)    NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuario_usuario (usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT       NOT NULL,
    token       VARCHAR(36)  NOT NULL,
    expira_en   DATETIME     NOT NULL,
    revocado    TINYINT(1)   NOT NULL DEFAULT 0,
    creado_en   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token (token),
    CONSTRAINT fk_rt_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- CONFIGURACION
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS negocio_config (
    id               BIGINT        NOT NULL,
    ruc_negocio      VARCHAR(11)   NULL,
    nombre_comercial VARCHAR(255)  NULL,
    direccion        VARCHAR(255)  NULL,
    logo_url         VARCHAR(255)  NULL,
    igv_porcentaje   DECIMAL(5,2)  NOT NULL DEFAULT 18.00,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS serie_comprobante (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    tipo               VARCHAR(1)  NOT NULL COMMENT 'B=Boleta, F=Factura, T=Ticket',
    serie              VARCHAR(4)  NOT NULL,
    correlativo_actual INT         NOT NULL DEFAULT 1,
    activo             TINYINT(1)  NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS impresora (
    id     BIGINT       NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(255) NOT NULL,
    tipo   VARCHAR(10)  NOT NULL COMMENT 'COCINA, CAJA, BARRA',
    host   VARCHAR(255) NULL,
    puerto INT          NOT NULL DEFAULT 0,
    activo TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- CATALOGO
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS categoria (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    nombre      VARCHAR(50)  NULL,
    descripcion VARCHAR(150) NULL,
    activo      TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS producto (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    categoria_id BIGINT         NULL,
    nombre      VARCHAR(80)     NULL,
    descripcion TEXT            NULL,
    precio      DECIMAL(10,2)   NULL,
    imagen_url  VARCHAR(255)    NULL,
    disponible  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- MESAS
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS mesa (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    numero    VARCHAR(5)  NULL,
    capacidad INT         NOT NULL DEFAULT 0,
    estado    VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE' COMMENT 'DISPONIBLE, OCUPADA, RESERVADA, INACTIVA',
    PRIMARY KEY (id),
    UNIQUE KEY uq_mesa_numero (numero)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sesion_mesa (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    mesa_id    BIGINT   NULL,
    mesero_id  BIGINT   NULL,
    abierta_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrada_en DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sesion_mesa FOREIGN KEY (mesa_id) REFERENCES mesa (id) ON DELETE SET NULL,
    CONSTRAINT fk_sesion_mesero FOREIGN KEY (mesero_id) REFERENCES usuario (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- PEDIDOS
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pedido (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    sesion_mesa_id BIGINT  NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'ABIERTO' COMMENT 'ABIERTO, EN_COCINA, LISTO, COBRADO, CANCELADO',
    observaciones TEXT     NULL,
    creado_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_pedido_sesion FOREIGN KEY (sesion_mesa_id) REFERENCES sesion_mesa (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS detalle_pedido (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT        NULL,
    producto_id     BIGINT        NULL,
    cantidad        INT           NOT NULL DEFAULT 1,
    precio_unitario DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    estado          VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE' COMMENT 'PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO',
    observaciones   TEXT          NULL,
    creado_en       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- CAJA
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS arqueo_caja (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    cajero_id      BIGINT        NOT NULL,
    nombre_cajero  VARCHAR(255)  NULL,
    apertura_en    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cierre_en      DATETIME      NULL,
    monto_apertura DECIMAL(10,2) NULL,
    monto_cierre   DECIMAL(10,2) NULL,
    total_ventas   DECIMAL(10,2) NULL,
    estado         VARCHAR(10)   NOT NULL DEFAULT 'ABIERTO' COMMENT 'ABIERTO, CERRADO',
    notas          VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_arqueo_cajero FOREIGN KEY (cajero_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS datos_comprobante (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    tipo_comprobante_id VARCHAR(1)  NULL COMMENT 'B=Boleta, F=Factura, T=Ticket',
    ruc_dni             VARCHAR(11) NULL,
    razon_social        VARCHAR(255) NULL,
    direccion           VARCHAR(255) NULL,
    creado_en           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comprobante_venta (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    pedido_id           BIGINT        NOT NULL,
    cajero_id           BIGINT        NOT NULL,
    tipo_comprobante_id VARCHAR(1)    NOT NULL DEFAULT 'T' COMMENT 'B=Boleta, F=Factura, T=Ticket',
    datos_comprobante_id BIGINT       NULL,
    subtotal            DECIMAL(10,2) NULL,
    igv                 DECIMAL(10,2) NULL,
    total               DECIMAL(10,2) NULL,
    metodo_pago         VARCHAR(20)   NOT NULL COMMENT 'EFECTIVO, TARJETA, IZIPAY, YAPE, PLIN, TRANSFERENCIA',
    estado              VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE' COMMENT 'PENDIENTE, COMPLETADO, ANULADO',
    pagado_en           DATETIME      NULL,
    creado_en           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_comprobante_pedido (pedido_id),
    CONSTRAINT fk_comprobante_pedido    FOREIGN KEY (pedido_id)            REFERENCES pedido (id),
    CONSTRAINT fk_comprobante_cajero    FOREIGN KEY (cajero_id)            REFERENCES usuario (id),
    CONSTRAINT fk_comprobante_datos     FOREIGN KEY (datos_comprobante_id) REFERENCES datos_comprobante (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- PROVEEDORES
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS proveedor (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    nombre   VARCHAR(255) NOT NULL,
    ruc      VARCHAR(11)  NULL,
    telefono VARCHAR(255) NULL,
    correo   VARCHAR(255) NULL,
    contacto VARCHAR(255) NULL,
    activo   TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------------
-- STOCK
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS insumo (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    nombre       VARCHAR(255) NOT NULL,
    unidad       VARCHAR(20)  NOT NULL,
    stock_actual DOUBLE       NOT NULL DEFAULT 0,
    stock_minimo DOUBLE       NOT NULL DEFAULT 0,
    activo       TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
--  DATOS INICIALES
-- =============================================================

-- Usuario administrador por defecto
-- Contraseña: admin123 (BCrypt)
INSERT IGNORE INTO usuario (nombre, apellido, usuario, contrasena_hash, rol_id, activo)
VALUES ('Administrador', 'Sistema', 'admin',
        '$2a$12$ARv5I6Vv97qjUDI7Eea72ug1gQ5bhEZ3g2HRanuYdYwZpWCXLLJ.y',
        'AD', 1);

-- Configuración del negocio (fila única id=1)
INSERT IGNORE INTO negocio_config (id, nombre_comercial, igv_porcentaje)
VALUES (1, 'Mi Restaurante', 18.00);

-- Series por defecto
INSERT IGNORE INTO serie_comprobante (tipo, serie, correlativo_actual, activo) VALUES
('T', 'T001', 1, 1),
('B', 'B001', 1, 1),
('F', 'F001', 1, 1);

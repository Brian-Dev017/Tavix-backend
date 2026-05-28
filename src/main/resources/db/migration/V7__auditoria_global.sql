DROP PROCEDURE IF EXISTS create_auditoria_global;

DELIMITER //

CREATE PROCEDURE create_auditoria_global()
BEGIN
    CREATE TABLE IF NOT EXISTS auditoria_global (
        id BIGINT NOT NULL AUTO_INCREMENT,
        modulo VARCHAR(100) NOT NULL,
        tabla_nombre VARCHAR(100) NOT NULL,
        registro_id VARCHAR(64) NOT NULL,
        accion VARCHAR(30) NOT NULL,
        descripcion VARCHAR(255) NULL,
        valor_anterior JSON NULL,
        valor_nuevo JSON NULL,
        usuario_id BIGINT NULL,
        usuario_login VARCHAR(100) NULL,
        rol_id VARCHAR(20) NULL,
        ip_origen VARCHAR(45) NULL,
        endpoint VARCHAR(255) NULL,
        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id)
    );

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auditoria_global'
          AND index_name = 'idx_auditoria_global_tabla_registro'
    ) THEN
        CREATE INDEX idx_auditoria_global_tabla_registro
            ON auditoria_global (tabla_nombre, registro_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auditoria_global'
          AND index_name = 'idx_auditoria_global_usuario_id'
    ) THEN
        CREATE INDEX idx_auditoria_global_usuario_id
            ON auditoria_global (usuario_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auditoria_global'
          AND index_name = 'idx_auditoria_global_accion'
    ) THEN
        CREATE INDEX idx_auditoria_global_accion
            ON auditoria_global (accion);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auditoria_global'
          AND index_name = 'idx_auditoria_global_creado_en'
    ) THEN
        CREATE INDEX idx_auditoria_global_creado_en
            ON auditoria_global (creado_en);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'auditoria_global'
          AND index_name = 'idx_auditoria_global_modulo'
    ) THEN
        CREATE INDEX idx_auditoria_global_modulo
            ON auditoria_global (modulo);
    END IF;
END//

DELIMITER ;

CALL create_auditoria_global();

DROP PROCEDURE IF EXISTS create_auditoria_global;

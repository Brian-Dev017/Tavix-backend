DROP PROCEDURE IF EXISTS ensure_auth_session_indexes;

DELIMITER //

CREATE PROCEDURE ensure_auth_session_indexes()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'usuario'
          AND column_name = 'usuario'
          AND non_unique = 0
    ) THEN
        CREATE UNIQUE INDEX uk_usuario_usuario
            ON usuario (usuario);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'refresh_token'
          AND column_name = 'token'
          AND non_unique = 0
    ) THEN
        CREATE UNIQUE INDEX uk_refresh_token_token
            ON refresh_token (token);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'refresh_token'
          AND column_name = 'usuario_id'
          AND seq_in_index = 1
    ) THEN
        CREATE INDEX idx_refresh_token_usuario_id
            ON refresh_token (usuario_id);
    END IF;
END//

DELIMITER ;

CALL ensure_auth_session_indexes();

DROP PROCEDURE IF EXISTS ensure_auth_session_indexes;

DROP PROCEDURE IF EXISTS renombrar_mesa_para_llevar;

DELIMITER //

CREATE PROCEDURE renombrar_mesa_para_llevar()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'mesa'
          AND column_name = 'numero'
          AND character_maximum_length < 20
    ) THEN
        ALTER TABLE mesa
            MODIFY COLUMN numero VARCHAR(20) NOT NULL;
    END IF;

    UPDATE mesa
    SET numero = 'Para llevar',
        capacidad = 1,
        estado = 'DISPONIBLE',
        tipo = 'PARA_LLEVAR'
    WHERE tipo = 'PARA_LLEVAR'
       OR UPPER(numero) = 'LLEVA'
       OR UPPER(numero) = 'PARA LLEVAR';
END//

DELIMITER ;

CALL renombrar_mesa_para_llevar();

DROP PROCEDURE IF EXISTS renombrar_mesa_para_llevar;

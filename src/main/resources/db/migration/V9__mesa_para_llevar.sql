DROP PROCEDURE IF EXISTS add_tipo_mesa;

DELIMITER //

CREATE PROCEDURE add_tipo_mesa()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'mesa'
          AND column_name = 'tipo'
    ) THEN
        ALTER TABLE mesa
            ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SALON' AFTER estado;
    END IF;

    UPDATE mesa
    SET tipo = 'SALON'
    WHERE tipo IS NULL OR tipo = '';

    IF EXISTS (SELECT 1 FROM mesa WHERE UPPER(numero) = 'LLEVA') THEN
        UPDATE mesa
        SET capacidad = 1,
            estado = 'DISPONIBLE',
            tipo = 'PARA_LLEVAR'
        WHERE UPPER(numero) = 'LLEVA';
    ELSE
        INSERT INTO mesa (numero, capacidad, estado, tipo)
        VALUES ('LLEVA', 1, 'DISPONIBLE', 'PARA_LLEVAR');
    END IF;
END//

DELIMITER ;

CALL add_tipo_mesa();

DROP PROCEDURE IF EXISTS add_tipo_mesa;

CREATE OR REPLACE VIEW v_cola_cocina AS
SELECT
    dp.id AS detalle_id,
    p.id AS pedido_id,
    m.numero AS mesa,
    pr.nombre AS producto,
    dp.cantidad AS cantidad,
    dp.observaciones AS observaciones,
    dp.estado AS estado_item,
    dp.creado_en AS solicitado_en
FROM detalle_pedido dp
JOIN pedido p ON p.id = dp.pedido_id
JOIN producto pr ON pr.id = dp.producto_id
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
WHERE dp.estado IN ('PENDIENTE', 'EN_PREPARACION')
  AND (
      p.estado = 'EN_COCINA'
      OR (p.estado = 'COBRADO' AND m.tipo = 'PARA_LLEVAR')
  );

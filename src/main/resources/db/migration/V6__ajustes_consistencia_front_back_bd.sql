DROP PROCEDURE IF EXISTS add_total_redondeo_arqueo;

DELIMITER //

CREATE PROCEDURE add_total_redondeo_arqueo()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'arqueo_caja'
          AND column_name = 'total_redondeo'
    ) THEN
        ALTER TABLE arqueo_caja
            ADD COLUMN total_redondeo DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_digital;
    END IF;
END//

DELIMITER ;

CALL add_total_redondeo_arqueo();

DROP PROCEDURE IF EXISTS add_total_redondeo_arqueo;

CREATE OR REPLACE VIEW v_consumo_por_pedido AS
SELECT
    p.id AS pedido_id,
    m.numero AS mesa,
    CONCAT(u.nombre, ' ', u.apellido) AS mesero,
    COALESCE(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad ELSE 0 END), 0) AS total_items,
    COALESCE(ROUND(SUM(
        CASE WHEN dp.estado <> 'CANCELADO'
            THEN dp.cantidad * dp.precio_unitario
            ELSE 0
        END
    ), 2), 0.00) AS subtotal,
    COALESCE(ROUND(SUM(
        CASE WHEN dp.estado <> 'CANCELADO'
            THEN dp.cantidad * dp.precio_unitario
            ELSE 0
        END
    ) * (COALESCE(nc.igv_porcentaje, 18.00) / 100), 2), 0.00) AS igv,
    COALESCE(ROUND(SUM(
        CASE WHEN dp.estado <> 'CANCELADO'
            THEN dp.cantidad * dp.precio_unitario
            ELSE 0
        END
    ) * (1 + (COALESCE(nc.igv_porcentaje, 18.00) / 100)), 2), 0.00) AS total_con_igv,
    p.estado AS estado_pedido
FROM pedido p
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
LEFT JOIN usuario u ON u.id = sm.mesero_id
LEFT JOIN detalle_pedido dp ON dp.pedido_id = p.id
LEFT JOIN negocio_config nc ON nc.id = 1
GROUP BY p.id, m.numero, u.nombre, u.apellido, p.estado, nc.igv_porcentaje;

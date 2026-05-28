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
    ) * 0.18, 2), 0.00) AS igv,
    COALESCE(ROUND(SUM(
        CASE WHEN dp.estado <> 'CANCELADO'
            THEN dp.cantidad * dp.precio_unitario
            ELSE 0
        END
    ), 2), 0.00) AS total_con_igv,
    p.estado AS estado_pedido
FROM pedido p
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
LEFT JOIN usuario u ON u.id = sm.mesero_id
LEFT JOIN detalle_pedido dp ON dp.pedido_id = p.id
GROUP BY p.id, m.numero, u.nombre, u.apellido, p.estado;

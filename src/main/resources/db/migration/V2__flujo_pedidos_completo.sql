ALTER TABLE detalle_pedido
    ADD COLUMN motivo_cancelacion VARCHAR(255) NULL,
    ADD COLUMN cancelado_en DATETIME NULL;

ALTER TABLE pedido
    ADD COLUMN motivo_cancelacion VARCHAR(255) NULL,
    ADD COLUMN cancelado_en DATETIME NULL;

ALTER TABLE comprobante_venta
    ADD COLUMN serie VARCHAR(4) NULL,
    ADD COLUMN numero INT NULL,
    ADD COLUMN descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN motivo_descuento VARCHAR(255) NULL;

DROP VIEW IF EXISTS v_cola_cocina;
CREATE VIEW v_cola_cocina AS
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
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
LEFT JOIN producto pr ON pr.id = dp.producto_id
WHERE p.estado IN ('EN_COCINA', 'LISTO')
  AND dp.estado IN ('PENDIENTE', 'EN_PREPARACION')
ORDER BY dp.creado_en ASC;

DROP VIEW IF EXISTS v_consumo_por_pedido;
CREATE VIEW v_consumo_por_pedido AS
SELECT
    p.id AS pedido_id,
    m.numero AS mesa,
    CONCAT(u.nombre, ' ', u.apellido) AS mesero,
    COALESCE(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad ELSE 0 END), 0) AS total_items,
    COALESCE(ROUND(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END), 2), 0.00) AS subtotal,
    COALESCE(ROUND(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END) * 0.18, 2), 0.00) AS igv,
    COALESCE(ROUND(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END) * 1.18, 2), 0.00) AS total_con_igv,
    p.estado AS estado_pedido
FROM pedido p
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
LEFT JOIN usuario u ON u.id = sm.mesero_id
LEFT JOIN detalle_pedido dp ON dp.pedido_id = p.id
GROUP BY p.id, m.numero, u.nombre, u.apellido, p.estado;

DROP TRIGGER IF EXISTS tr_detalle_insert_en_cocina;
CREATE TRIGGER tr_detalle_insert_en_cocina
AFTER INSERT ON detalle_pedido
FOR EACH ROW
UPDATE pedido
SET estado = 'EN_COCINA'
WHERE id = NEW.pedido_id
  AND estado = 'ABIERTO';

DROP TRIGGER IF EXISTS tr_detalle_update_pedido_listo;
CREATE TRIGGER tr_detalle_update_pedido_listo
AFTER UPDATE ON detalle_pedido
FOR EACH ROW
UPDATE pedido p
SET p.estado = CASE
    WHEN EXISTS (
        SELECT 1 FROM detalle_pedido d
        WHERE d.pedido_id = p.id
          AND d.estado <> 'CANCELADO'
    )
    AND NOT EXISTS (
        SELECT 1 FROM detalle_pedido d
        WHERE d.pedido_id = p.id
          AND d.estado NOT IN ('LISTO', 'CANCELADO')
    )
    THEN 'LISTO'
    ELSE p.estado
END
WHERE p.id = NEW.pedido_id
  AND p.estado IN ('ABIERTO', 'EN_COCINA', 'LISTO');

DROP TRIGGER IF EXISTS tr_comprobante_cobrar_pedido;
CREATE TRIGGER tr_comprobante_cobrar_pedido
AFTER UPDATE ON comprobante_venta
FOR EACH ROW
UPDATE pedido
SET estado = 'COBRADO'
WHERE id = NEW.pedido_id
  AND NEW.estado = 'COMPLETADO'
  AND OLD.estado <> NEW.estado;

DROP TRIGGER IF EXISTS tr_pedido_cobrado_cerrar_sesion;
CREATE TRIGGER tr_pedido_cobrado_cerrar_sesion
AFTER UPDATE ON pedido
FOR EACH ROW
UPDATE sesion_mesa
SET cerrada_en = COALESCE(cerrada_en, CURRENT_TIMESTAMP)
WHERE id = NEW.sesion_mesa_id
  AND NEW.estado = 'COBRADO'
  AND OLD.estado <> NEW.estado;

DROP TRIGGER IF EXISTS tr_sesion_cerrada_liberar_mesa;
CREATE TRIGGER tr_sesion_cerrada_liberar_mesa
AFTER UPDATE ON sesion_mesa
FOR EACH ROW
UPDATE mesa
SET estado = 'DISPONIBLE'
WHERE id = NEW.mesa_id
  AND NEW.cerrada_en IS NOT NULL
  AND OLD.cerrada_en IS NULL;

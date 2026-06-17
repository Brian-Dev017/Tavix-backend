-- =============================================================
--  V13 — IGV correcto, ruteo de cocina, costos y reportes
--  - v_consumo_por_pedido: el precio INCLUYE IGV; el IGV se
--    EXTRAE (base = bruto / (1+igv%)), respetando afectación.
--  - producto.requiere_cocina: bebidas/gaseosas no van a cocina.
--  - producto.costo: para reportes de rentabilidad.
--  - tabla gasto: reporte de gastos del restaurante.
--  Idempotente.
-- =============================================================

DROP PROCEDURE IF EXISTS v13_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE v13_add_column_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- Catálogo: ruteo de cocina y costo
CALL v13_add_column_if_missing('producto', 'requiere_cocina',
     "TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=no pasa por cocina (bebidas)' AFTER disponible");
CALL v13_add_column_if_missing('producto', 'costo',
     "DECIMAL(10,2) NULL COMMENT 'Costo unitario para rentabilidad' AFTER precio");

DROP PROCEDURE IF EXISTS v13_add_column_if_missing;

-- Las bebidas de categoría 'Bebidas' por defecto no pasan por cocina
UPDATE producto p
   JOIN categoria c ON c.id = p.categoria_id
   SET p.requiere_cocina = 0
 WHERE LOWER(c.nombre) LIKE '%bebida%';

-- -------------------------------------------------------------
-- Tabla de gastos (reporte de gastos del restaurante)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gasto (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    fecha          DATE          NOT NULL,
    categoria      VARCHAR(20)   NOT NULL COMMENT 'INSUMOS, SERVICIOS, PERSONAL, DELIVERY, MANTENIMIENTO, OTROS',
    descripcion    VARCHAR(255)  NULL,
    monto          DECIMAL(10,2) NOT NULL,
    registrado_por BIGINT        NULL,
    creado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_gasto_fecha (fecha),
    KEY idx_gasto_categoria (categoria),
    CONSTRAINT ck_gasto_monto CHECK (monto >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Índice para "una apertura de caja por día por usuario"
DROP PROCEDURE IF EXISTS v13_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE v13_add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX ', p_index, ' ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL v13_add_index_if_missing('arqueo_caja', 'idx_arqueo_cajero_apertura', '(cajero_id, apertura_en)');
DROP PROCEDURE IF EXISTS v13_add_index_if_missing;

-- -------------------------------------------------------------
-- v_consumo_por_pedido — IGV EXTRAÍDO (precio incluye IGV)
-- subtotal = base imponible; igv extraído sólo de lo GRAVADO;
-- total_con_igv = bruto (lo que paga el cliente).
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_consumo_por_pedido AS
SELECT
    p.id AS pedido_id,
    m.numero AS mesa,
    CONCAT(u.nombre, ' ', u.apellido) AS mesero,
    COALESCE(SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad ELSE 0 END), 0) AS total_items,
    -- base imponible total = bruto_total - igv
    COALESCE(ROUND(
        SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END)
        - SUM(CASE WHEN dp.estado <> 'CANCELADO' AND COALESCE(pr.afectacion_igv,'GRAVADO') = 'GRAVADO'
                   THEN dp.cantidad * dp.precio_unitario
                        - ROUND(dp.cantidad * dp.precio_unitario / (1 + COALESCE(nc.igv_porcentaje,18.00)/100), 2)
                   ELSE 0 END)
    , 2), 0.00) AS subtotal,
    -- IGV extraído sólo de los ítems gravados
    COALESCE(ROUND(
        SUM(CASE WHEN dp.estado <> 'CANCELADO' AND COALESCE(pr.afectacion_igv,'GRAVADO') = 'GRAVADO'
                 THEN dp.cantidad * dp.precio_unitario
                      - ROUND(dp.cantidad * dp.precio_unitario / (1 + COALESCE(nc.igv_porcentaje,18.00)/100), 2)
                 ELSE 0 END)
    , 2), 0.00) AS igv,
    -- total = bruto (precio ya incluye IGV)
    COALESCE(ROUND(
        SUM(CASE WHEN dp.estado <> 'CANCELADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END)
    , 2), 0.00) AS total_con_igv,
    p.estado AS estado_pedido
FROM pedido p
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
LEFT JOIN usuario u ON u.id = sm.mesero_id
LEFT JOIN detalle_pedido dp ON dp.pedido_id = p.id
LEFT JOIN producto pr ON pr.id = dp.producto_id
LEFT JOIN negocio_config nc ON nc.id = 1
GROUP BY p.id, m.numero, u.nombre, u.apellido, p.estado;

-- -------------------------------------------------------------
-- v_cola_cocina — excluir productos que no requieren cocina
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_cola_cocina AS
SELECT
    dp.id AS detalle_id, p.id AS pedido_id, m.numero AS mesa, pr.nombre AS producto,
    dp.cantidad AS cantidad, dp.observaciones AS observaciones, dp.estado AS estado_item,
    dp.creado_en AS solicitado_en
FROM detalle_pedido dp
JOIN pedido p ON p.id = dp.pedido_id
JOIN producto pr ON pr.id = dp.producto_id
JOIN sesion_mesa sm ON sm.id = p.sesion_mesa_id
JOIN mesa m ON m.id = sm.mesa_id
WHERE dp.estado IN ('PENDIENTE', 'EN_PREPARACION')
  AND COALESCE(pr.requiere_cocina, 1) = 1
  AND (
      p.estado = 'EN_COCINA'
      OR (p.estado = 'COBRADO' AND m.tipo = 'PARA_LLEVAR')
  );

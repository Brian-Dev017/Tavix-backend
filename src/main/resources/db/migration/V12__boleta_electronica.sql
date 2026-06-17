-- =============================================================
--  V12 — Boleta de Venta Electrónica (estilo SUNAT)
--  Objetivo : emisión de comprobante con serie+correlativo
--             atómicos, desglose OP. GRAVADA/EXONERADA/INAFECTA,
--             IGV, importe total, monto en letras, efectivo y vuelto.
--  Modelo   : "precio incluye IGV" (coherente con V8).
--  Idempotente: puede re-ejecutarse sin error.
-- =============================================================

-- -------------------------------------------------------------
-- 1) Ajustes de esquema (idempotentes vía information_schema)
-- -------------------------------------------------------------
DROP PROCEDURE IF EXISTS v12_add_column_if_missing;

DELIMITER $$
CREATE PROCEDURE v12_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- Catálogo: código de barras y categoría tributaria por producto
CALL v12_add_column_if_missing('producto', 'codigo',
     'VARCHAR(20) NULL AFTER nombre');
CALL v12_add_column_if_missing('producto', 'afectacion_igv',
     "VARCHAR(10) NOT NULL DEFAULT 'GRAVADO' COMMENT 'GRAVADO, EXONERADO, INAFECTO' AFTER precio");

-- Emisor: razón social (la boleta muestra razón social + nombre comercial)
CALL v12_add_column_if_missing('negocio_config', 'razon_social',
     'VARCHAR(255) NULL AFTER nombre_comercial');

-- Comprobante: se PERSISTE el desglose fiscal al emitir (inmutable/auditable)
CALL v12_add_column_if_missing('comprobante_venta', 'op_gravada',
     'DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER subtotal');
CALL v12_add_column_if_missing('comprobante_venta', 'op_exonerada',
     'DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER op_gravada');
CALL v12_add_column_if_missing('comprobante_venta', 'op_inafecta',
     'DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER op_exonerada');
CALL v12_add_column_if_missing('comprobante_venta', 'total_unidades',
     'INT NOT NULL DEFAULT 0 AFTER op_inafecta');
CALL v12_add_column_if_missing('comprobante_venta', 'monto_en_letras',
     'VARCHAR(255) NULL AFTER total_unidades');

DROP PROCEDURE IF EXISTS v12_add_column_if_missing;

-- -------------------------------------------------------------
-- 2) Emisor (MASS) — datos reales del negocio
-- -------------------------------------------------------------
UPDATE negocio_config
   SET ruc_negocio      = '20608280333',
       razon_social     = 'COMPANIA HARD DISCOUNT S.A.C.',
       nombre_comercial = 'MASS',
       direccion        = 'CAL. CESAR MORELLI 139 P-3 SAN BORJA, LIMA',
       igv_porcentaje   = COALESCE(igv_porcentaje, 18.00)
 WHERE id = 1;

-- -------------------------------------------------------------
-- 3) Función: monto numérico -> letras (español, formato SUNAT)
--    Ej: 21.50 -> 'VEINTIUN Y 50/100 SOLES'
-- -------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_apocope;
DROP FUNCTION IF EXISTS fn_letras_0_29;
DROP FUNCTION IF EXISTS fn_letras_0_99;
DROP FUNCTION IF EXISTS fn_letras_0_999;
DROP FUNCTION IF EXISTS fn_numero_a_letras;

DELIMITER $$

-- Apócope final: "...UNO" -> "...UN" (antes de SOLES / MIL / MILLONES)
CREATE FUNCTION fn_apocope(p_txt VARCHAR(255))
RETURNS VARCHAR(255) DETERMINISTIC
BEGIN
    IF RIGHT(p_txt, 3) = 'UNO' THEN
        RETURN CONCAT(LEFT(p_txt, CHAR_LENGTH(p_txt) - 3), 'UN');
    END IF;
    RETURN p_txt;
END$$

CREATE FUNCTION fn_letras_0_29(n INT)
RETURNS VARCHAR(40) DETERMINISTIC
BEGIN
    RETURN ELT(n + 1,
        '', 'UNO', 'DOS', 'TRES', 'CUATRO', 'CINCO', 'SEIS', 'SIETE', 'OCHO', 'NUEVE',
        'DIEZ', 'ONCE', 'DOCE', 'TRECE', 'CATORCE', 'QUINCE', 'DIECISEIS', 'DIECISIETE',
        'DIECIOCHO', 'DIECINUEVE', 'VEINTE', 'VEINTIUNO', 'VEINTIDOS', 'VEINTITRES',
        'VEINTICUATRO', 'VEINTICINCO', 'VEINTISEIS', 'VEINTISIETE', 'VEINTIOCHO', 'VEINTINUEVE');
END$$

CREATE FUNCTION fn_letras_0_99(n INT)
RETURNS VARCHAR(60) DETERMINISTIC
BEGIN
    DECLARE v_dec INT; DECLARE v_uni INT; DECLARE v_tname VARCHAR(20);
    IF n < 30 THEN RETURN fn_letras_0_29(n); END IF;
    SET v_dec = FLOOR(n / 10); SET v_uni = n MOD 10;
    SET v_tname = ELT(v_dec - 2, 'TREINTA', 'CUARENTA', 'CINCUENTA',
                      'SESENTA', 'SETENTA', 'OCHENTA', 'NOVENTA');
    IF v_uni = 0 THEN RETURN v_tname; END IF;
    RETURN CONCAT(v_tname, ' Y ', fn_letras_0_29(v_uni));
END$$

CREATE FUNCTION fn_letras_0_999(n INT)
RETURNS VARCHAR(120) DETERMINISTIC
BEGIN
    DECLARE v_cen INT; DECLARE v_resto INT; DECLARE v_cname VARCHAR(20); DECLARE v_dname VARCHAR(60);
    IF n = 0 THEN RETURN ''; END IF;
    SET v_cen = FLOOR(n / 100); SET v_resto = n MOD 100;
    IF v_cen = 0 THEN SET v_cname = '';
    ELSEIF v_cen = 1 THEN SET v_cname = IF(v_resto = 0, 'CIEN', 'CIENTO');
    ELSE SET v_cname = ELT(v_cen - 1, 'DOSCIENTOS', 'TRESCIENTOS', 'CUATROCIENTOS',
            'QUINIENTOS', 'SEISCIENTOS', 'SETECIENTOS', 'OCHOCIENTOS', 'NOVECIENTOS');
    END IF;
    SET v_dname = fn_letras_0_99(v_resto);
    RETURN TRIM(CONCAT_WS(' ', NULLIF(v_cname, ''), NULLIF(v_dname, '')));
END$$

CREATE FUNCTION fn_numero_a_letras(p_monto DECIMAL(12,2))
RETURNS VARCHAR(255) DETERMINISTIC
BEGIN
    DECLARE v_total_cent BIGINT; DECLARE v_entero BIGINT; DECLARE v_cent INT;
    DECLARE v_millon INT; DECLARE v_resto1 INT; DECLARE v_miles INT; DECLARE v_cientos INT;
    DECLARE v_mill_txt VARCHAR(120); DECLARE v_miles_txt VARCHAR(120);
    DECLARE v_cientos_txt VARCHAR(120); DECLARE v_entero_txt VARCHAR(255);

    SET v_total_cent = ROUND(p_monto * 100);
    SET v_entero = FLOOR(v_total_cent / 100);
    SET v_cent = v_total_cent - (v_entero * 100);

    SET v_millon  = FLOOR(v_entero / 1000000);
    SET v_resto1  = v_entero MOD 1000000;
    SET v_miles   = FLOOR(v_resto1 / 1000);
    SET v_cientos = v_resto1 MOD 1000;

    SET v_mill_txt = '';
    IF v_millon = 1 THEN SET v_mill_txt = 'UN MILLON';
    ELSEIF v_millon > 1 THEN SET v_mill_txt = CONCAT(fn_apocope(fn_letras_0_999(v_millon)), ' MILLONES');
    END IF;

    SET v_miles_txt = '';
    IF v_miles = 1 THEN SET v_miles_txt = 'MIL';
    ELSEIF v_miles > 1 THEN SET v_miles_txt = CONCAT(fn_apocope(fn_letras_0_999(v_miles)), ' MIL');
    END IF;

    SET v_cientos_txt = fn_letras_0_999(v_cientos);

    SET v_entero_txt = TRIM(CONCAT_WS(' ',
        NULLIF(v_mill_txt, ''), NULLIF(v_miles_txt, ''), NULLIF(v_cientos_txt, '')));
    IF v_entero_txt = '' THEN SET v_entero_txt = 'CERO'; END IF;

    RETURN CONCAT(fn_apocope(v_entero_txt), ' Y ', LPAD(v_cent, 2, '0'), '/100 SOLES');
END$$

DELIMITER ;

-- -------------------------------------------------------------
-- 4) Procedimiento de emisión atómica del comprobante
-- -------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_emitir_comprobante;

DELIMITER $$
CREATE PROCEDURE sp_emitir_comprobante(
    IN  p_pedido_id            BIGINT,
    IN  p_cajero_id            BIGINT,
    IN  p_tipo                 CHAR(1),        -- 'B' Boleta, 'F' Factura, 'T' Ticket
    IN  p_metodo_pago          VARCHAR(20),    -- EFECTIVO, TARJETA, YAPE, ...
    IN  p_efectivo_recibido    DECIMAL(10,2),  -- NULL si no es efectivo
    IN  p_datos_comprobante_id BIGINT,         -- receptor (RUC/DNI), NULL si no aplica
    IN  p_descuento            DECIMAL(10,2),
    OUT p_comprobante_id       BIGINT
)
sp: BEGIN
    DECLARE v_igv_pct      DECIMAL(5,2);
    DECLARE v_unidades     INT;
    DECLARE v_bruto_grav   DECIMAL(12,2);
    DECLARE v_op_gravada   DECIMAL(12,2);
    DECLARE v_igv          DECIMAL(12,2);
    DECLARE v_op_exonerada DECIMAL(12,2);
    DECLARE v_op_inafecta  DECIMAL(12,2);
    DECLARE v_subtotal     DECIMAL(12,2);
    DECLARE v_total        DECIMAL(12,2);
    DECLARE v_desc         DECIMAL(12,2);
    DECLARE v_serie        VARCHAR(4);
    DECLARE v_correlativo  INT;
    DECLARE v_vuelto       DECIMAL(12,2);
    DECLARE v_estado_pedido VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET v_desc = COALESCE(p_descuento, 0.00);

    START TRANSACTION;

    -- (a) Validar pedido y que no esté ya facturado
    SELECT estado INTO v_estado_pedido FROM pedido WHERE id = p_pedido_id FOR UPDATE;
    IF v_estado_pedido IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido inexistente';
    END IF;
    IF v_estado_pedido IN ('COBRADO', 'CANCELADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido ya fue cobrado o cancelado';
    END IF;
    IF EXISTS (SELECT 1 FROM comprobante_venta WHERE pedido_id = p_pedido_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido ya tiene un comprobante emitido';
    END IF;

    -- (b) IGV vigente del negocio
    SELECT COALESCE(igv_porcentaje, 18.00) INTO v_igv_pct FROM negocio_config WHERE id = 1;
    SET v_igv_pct = COALESCE(v_igv_pct, 18.00);

    -- (c) Totales y desglose tributario desde el detalle (precio incluye IGV)
    SELECT
        COALESCE(SUM(dp.cantidad), 0),
        COALESCE(SUM(CASE WHEN pr.afectacion_igv = 'GRAVADO'   THEN dp.cantidad * dp.precio_unitario ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN pr.afectacion_igv = 'EXONERADO' THEN dp.cantidad * dp.precio_unitario ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN pr.afectacion_igv = 'INAFECTO'  THEN dp.cantidad * dp.precio_unitario ELSE 0 END), 0)
      INTO v_unidades, v_bruto_grav, v_op_exonerada, v_op_inafecta
      FROM detalle_pedido dp
      JOIN producto pr ON pr.id = dp.producto_id
     WHERE dp.pedido_id = p_pedido_id
       AND dp.estado <> 'CANCELADO';

    IF v_unidades = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido no tiene items facturables';
    END IF;

    SET v_op_gravada = ROUND(v_bruto_grav / (1 + v_igv_pct / 100), 2);
    SET v_igv        = ROUND(v_bruto_grav - v_op_gravada, 2);
    SET v_subtotal   = ROUND(v_op_gravada + v_op_exonerada + v_op_inafecta, 2);
    SET v_total      = ROUND(v_subtotal + v_igv - v_desc, 2);

    IF v_total < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El descuento supera el total';
    END IF;

    -- (d) Validación de pago en efectivo y vuelto
    IF p_metodo_pago = 'EFECTIVO' THEN
        IF p_efectivo_recibido IS NULL OR p_efectivo_recibido < v_total THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Efectivo recibido insuficiente';
        END IF;
        SET v_vuelto = ROUND(p_efectivo_recibido - v_total, 2);
    ELSE
        SET v_vuelto = 0.00;
    END IF;

    -- (e) Serie + correlativo atómicos (bloqueo de fila)
    SELECT serie, correlativo_actual INTO v_serie, v_correlativo
      FROM serie_comprobante
     WHERE tipo = p_tipo AND activo = 1
     ORDER BY id LIMIT 1
     FOR UPDATE;
    IF v_serie IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No hay serie activa para el tipo de comprobante';
    END IF;

    -- (f) Insertar (PENDIENTE) y luego COMPLETAR para disparar el flujo de cobro
    INSERT INTO comprobante_venta (
        pedido_id, cajero_id, tipo_comprobante_id, datos_comprobante_id,
        subtotal, op_gravada, op_exonerada, op_inafecta, igv, total,
        total_unidades, monto_en_letras,
        metodo_pago, efectivo_recibido, vuelto, descuento,
        serie, numero, estado
    ) VALUES (
        p_pedido_id, p_cajero_id, p_tipo, p_datos_comprobante_id,
        v_subtotal, v_op_gravada, v_op_exonerada, v_op_inafecta, v_igv, v_total,
        v_unidades, fn_numero_a_letras(v_total),
        p_metodo_pago, p_efectivo_recibido, v_vuelto, v_desc,
        v_serie, v_correlativo, 'PENDIENTE'
    );
    SET p_comprobante_id = LAST_INSERT_ID();

    UPDATE comprobante_venta
       SET estado = 'COMPLETADO', pagado_en = NOW()
     WHERE id = p_comprobante_id;

    -- (g) Avanzar correlativo
    UPDATE serie_comprobante
       SET correlativo_actual = correlativo_actual + 1
     WHERE tipo = p_tipo AND serie = v_serie AND activo = 1;

    COMMIT;
END$$
DELIMITER ;

-- -------------------------------------------------------------
-- 5) Vistas de render de la boleta
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_boleta_cabecera AS
SELECT
    cv.id                                            AS comprobante_id,
    nc.razon_social                                  AS emisor_razon_social,
    nc.nombre_comercial                              AS emisor_nombre_comercial,
    nc.ruc_negocio                                   AS emisor_ruc,
    nc.direccion                                     AS emisor_direccion,
    CASE cv.tipo_comprobante_id
         WHEN 'B' THEN 'BOLETA DE VENTA ELECTRONICA'
         WHEN 'F' THEN 'FACTURA ELECTRONICA'
         ELSE 'TICKET' END                           AS titulo,
    CONCAT(cv.serie, '-', LPAD(cv.numero, 8, '0'))   AS serie_correlativo,
    dc.ruc_dni                                       AS receptor_doc,
    dc.razon_social                                  AS receptor_nombre,
    cv.total_unidades,
    cv.subtotal,
    cv.op_exonerada,
    cv.op_inafecta,
    cv.op_gravada,
    cv.igv,
    cv.total                                         AS importe_total,
    cv.total                                         AS total_a_pagar,
    cv.monto_en_letras,
    cv.metodo_pago,
    cv.efectivo_recibido,
    cv.vuelto,
    cv.estado,
    cv.pagado_en
FROM comprobante_venta cv
JOIN negocio_config nc ON nc.id = 1
LEFT JOIN datos_comprobante dc ON dc.id = cv.datos_comprobante_id;

CREATE OR REPLACE VIEW v_boleta_detalle AS
SELECT
    cv.id                                AS comprobante_id,
    pr.codigo                            AS codigo,
    pr.nombre                            AS descripcion,
    dp.cantidad                          AS cantidad,
    dp.precio_unitario                   AS precio_unitario,
    ROUND(dp.cantidad * dp.precio_unitario, 2) AS importe,
    pr.afectacion_igv                    AS afectacion
FROM comprobante_venta cv
JOIN detalle_pedido dp ON dp.pedido_id = cv.pedido_id AND dp.estado <> 'CANCELADO'
JOIN producto pr       ON pr.id = dp.producto_id;

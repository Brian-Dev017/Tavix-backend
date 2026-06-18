UPDATE producto p
JOIN categoria c ON c.id = p.categoria_id
SET p.requiere_cocina = 0
WHERE LOWER(TRIM(c.nombre)) IN ('bebidas frías', 'bebidas frias');

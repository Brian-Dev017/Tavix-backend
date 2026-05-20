-- Corrige la contraseña inicial documentada del usuario administrador.
-- Usuario: admin
-- Contraseña: admin123
INSERT IGNORE INTO usuario (nombre, apellido, usuario, contrasena_hash, rol_id, activo)
VALUES ('Administrador', 'Sistema', 'admin',
        '$2a$12$ARv5I6Vv97qjUDI7Eea72ug1gQ5bhEZ3g2HRanuYdYwZpWCXLLJ.y',
        'AD', 1);

UPDATE usuario
SET contrasena_hash = '$2a$12$ARv5I6Vv97qjUDI7Eea72ug1gQ5bhEZ3g2HRanuYdYwZpWCXLLJ.y',
    activo = 1,
    rol_id = 'AD'
WHERE usuario = 'admin';

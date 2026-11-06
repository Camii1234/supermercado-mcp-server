-- ============================================================
-- Script de creación de la base de datos `tareas`
-- Ejecutar una sola vez antes de levantar el servidor MCP.
-- Ejecuta en WinPowerShell PS C:\Users\Diego\Downloads\tarea-mcp\tarea-mcp> mysql -u root -p -e "source C:/Users/Diego/Downloads/tarea-mcp/tarea-mcp/src/main/resources/schema.sql"
-- ============================================================

CREATE DATABASE IF NOT EXISTS labmcp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE labmcp;

CREATE TABLE IF NOT EXISTS tarea (
                                     id         BIGINT        NOT NULL AUTO_INCREMENT,
                                     completado BIT(1)        DEFAULT b'0',
    fecha      VARCHAR(255)  NULL,
    titulo     VARCHAR(255)  NOT NULL,
    version    BIGINT        DEFAULT 0,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Datos de ejemplo opcionales
INSERT INTO tarea (titulo, fecha, completado, version) VALUES
                                                           ('Preparar charla MCP',   '2026-10-23', b'0', 0),
                                                           ('Revisar slides',        '2026-10-24', b'0', 0),
                                                           ('Probar demo en vivo',   '2026-10-25', b'1', 0);
														   
														 
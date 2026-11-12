-- ============================================================
-- Base de datos del servidor MCP de pedidos de supermercado a domicilio.
-- Requiere MySQL 8.0.19 o superior (restricciones CHECK y alias de fila
-- en INSERT ... ON DUPLICATE KEY UPDATE).
--
-- Script idempotente: puede ejecutarse varias veces sin error y sin duplicar
-- los productos semilla (se actualizan por SKU con ON DUPLICATE KEY UPDATE).
--
-- Ejecución desde la raíz del proyecto (PowerShell):
--   mysql -u root -p -e "source src/main/resources/schema.sql"
--
-- Todos los datos son sintéticos; no contiene información personal.
-- ============================================================

CREATE DATABASE IF NOT EXISTS supermercado_mcp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE supermercado_mcp;

-- ---------- Catálogo de productos ----------
CREATE TABLE IF NOT EXISTS producto (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    sku         VARCHAR(40)    NOT NULL,
    nombre      VARCHAR(120)   NOT NULL,
    categoria   VARCHAR(60)    NOT NULL,
    precio      DECIMAL(12, 2) NOT NULL,
    stock       INT            NOT NULL DEFAULT 0,
    disponible  BIT(1)         NOT NULL DEFAULT b'1',
    version     BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_producto_sku UNIQUE (sku),
    CONSTRAINT ck_producto_precio CHECK (precio > 0),
    CONSTRAINT ck_producto_stock CHECK (stock >= 0),
    INDEX idx_producto_nombre (nombre),
    INDEX idx_producto_categoria (categoria),
    INDEX idx_producto_disponible (disponible)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------- Pedidos ----------
CREATE TABLE IF NOT EXISTS pedido (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    cliente_nombre  VARCHAR(120)   NOT NULL,
    telefono        VARCHAR(30)    NOT NULL,
    direccion       VARCHAR(200)   NOT NULL,
    fecha_creacion  DATETIME(6)    NOT NULL,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'BORRADOR',
    total           DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    version         BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_pedido_estado CHECK (estado IN ('BORRADOR', 'CONFIRMADO', 'CANCELADO')),
    CONSTRAINT ck_pedido_total CHECK (total >= 0),
    INDEX idx_pedido_estado (estado),
    INDEX idx_pedido_fecha_creacion (fecha_creacion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------- Líneas de cada pedido ----------
CREATE TABLE IF NOT EXISTS detalle_pedido (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    pedido_id        BIGINT         NOT NULL,
    producto_id      BIGINT         NOT NULL,
    cantidad         INT            NOT NULL,
    precio_unitario  DECIMAL(12, 2) NOT NULL,
    subtotal         DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    -- Un producto aparece una sola vez por pedido; repetirlo incrementa la cantidad.
    CONSTRAINT uk_detalle_pedido_producto UNIQUE (pedido_id, producto_id),
    CONSTRAINT fk_detalle_pedido_pedido FOREIGN KEY (pedido_id)
        REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_pedido_producto FOREIGN KEY (producto_id)
        REFERENCES producto (id) ON DELETE RESTRICT,
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio_unitario CHECK (precio_unitario > 0),
    CONSTRAINT ck_detalle_subtotal CHECK (subtotal > 0),
    INDEX idx_detalle_pedido_producto (producto_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------- Productos semilla (precios en pesos colombianos) ----------
-- Al reejecutar el script se actualizan los datos descriptivos y el precio,
-- pero NO el stock, para no revertir las existencias descontadas por pedidos reales.
INSERT INTO producto (sku, nombre, categoria, precio, stock, disponible) VALUES
    ('ARR-001', 'Arroz blanco 1 kg',              'Granos',     4900.00, 120, b'1'),
    ('LEC-001', 'Leche entera 1 L',               'Lácteos',    4200.00,  80, b'1'),
    ('HUE-030', 'Huevos AA x 30',                 'Huevos',    18500.00,  40, b'1'),
    ('PAN-001', 'Pan tajado 500 g',               'Panadería',  6300.00,  35, b'1'),
    ('ACE-001', 'Aceite vegetal 1 L',             'Despensa',  11900.00,  50, b'1'),
    ('CAF-500', 'Café molido 500 g',              'Despensa',  19800.00,  45, b'1'),
    ('AZU-001', 'Azúcar blanca 1 kg',             'Despensa',   4500.00,  90, b'1'),
    ('PAS-500', 'Pasta spaghetti 500 g',          'Granos',     3800.00,  70, b'1'),
    ('POL-001', 'Pechuga de pollo 1 kg',          'Carnes',    17900.00,  25, b'1'),
    ('MAN-001', 'Manzana roja 1 kg',              'Frutas',     8900.00,  30, b'1'),
    ('JAB-003', 'Jabón de baño x 3',              'Aseo',       9600.00,  60, b'1'),
    ('PAP-012', 'Papel higiénico x 12 rollos',    'Aseo',      21500.00,  20, b'0')
AS nuevo
ON DUPLICATE KEY UPDATE
    nombre     = nuevo.nombre,
    categoria  = nuevo.categoria,
    precio     = nuevo.precio,
    disponible = nuevo.disponible;

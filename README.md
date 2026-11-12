# Servidor MCP de pedidos de supermercado a domicilio

Laboratorio académico del curso de **Arquitectura de Software** — Universidad de Antioquia.

Servidor [Model Context Protocol (MCP)](https://modelcontextprotocol.io) construido con Spring Boot y
Spring AI que permite a un asistente conversacional (chatbot) consultar el catálogo de un supermercado
y gestionar pedidos a domicilio: crearlos, agregar y retirar productos, confirmarlos y cancelarlos.

---

## 1. Problema

Un supermercado quiere recibir pedidos a domicilio mediante un asistente conversacional. El modelo
de lenguaje entiende lo que pide el cliente ("quiero dos libras de arroz y una leche"), pero no tiene
acceso al inventario ni puede registrar pedidos. Hace falta un componente que exponga esas
capacidades de forma segura, tipada y con reglas de negocio consistentes (stock, estados del pedido,
totales), sin que el modelo manipule directamente la base de datos.

## 2. Objetivo

Implementar un servidor MCP que publique **diez herramientas** de negocio sobre una base de datos
MySQL, de modo que cualquier cliente MCP (por ejemplo, un agente conversacional) pueda invocarlas
con argumentos validados y recibir respuestas estructuradas.

## 3. Alcance

Incluido:

- Catálogo de productos con SKU, categoría, precio en pesos colombianos (COP), stock y disponibilidad.
- Ciclo de vida del pedido: `BORRADOR` → `CONFIRMADO` → `CANCELADO` (o `BORRADOR` → `CANCELADO`).
- Descuento y restitución transaccional de inventario.
- Script MySQL idempotente con datos semilla sintéticos.
- Pruebas automatizadas con H2 en memoria.

Fuera de alcance en esta etapa: frontend, controladores REST, autenticación, Docker, CI/CD,
despliegue en la nube y el agente conversacional (Google ADK), que se abordan en etapas posteriores.

## 4. Arquitectura general

```
 Cliente MCP (chatbot / agente)
        │  JSON-RPC sobre Streamable HTTP  (POST/GET http://localhost:8088/mcp)
        ▼
 ┌─────────────────────────────────────────────────────────────┐
 │ Spring AI MCP Server (WebMVC, SYNC)                         │
 │   MethodToolCallbackProvider ──► SupermercadoToolsService   │  ← 10 métodos @Tool, transaccionales
 │                                     │  ValidacionesSupermercado (reglas centralizadas)
 │                                     │  DTO (records) ◄── entidades
 │                                     ▼
 │                       ProductoRepository / PedidoRepository  (Spring Data JPA)
 └─────────────────────────────────────┬───────────────────────┘
                                       ▼
                              MySQL 8 — supermercado_mcp
```

Capas:

| Capa | Paquete | Responsabilidad |
|------|---------|-----------------|
| Aplicación | `com.udea.supermercadomcp` | Arranque y registro de herramientas (`MethodToolCallbackProvider`). |
| Servicio / herramientas | `service` | `SupermercadoToolsService` (herramientas MCP, transacciones) y `ValidacionesSupermercado` (validación centralizada). |
| Dominio | `model` | Entidades JPA `Producto`, `Pedido`, `DetallePedido` y enum `EstadoPedido`. |
| Persistencia | `repository` | Repositorios Spring Data con consultas derivadas, JPQL y `@EntityGraph`. |
| Contrato de salida | `dto` | Records inmutables que se serializan a JSON para el cliente MCP. |
| Errores | `exception` | `DatoInvalidoException`, `RecursoNoEncontradoException`, `ReglaNegocioException`. |

## 5. ¿Qué es MCP?

**Model Context Protocol** es un protocolo abierto, basado en JSON-RPC 2.0, que estandariza cómo una
aplicación de IA descubre y usa capacidades externas. Un *servidor MCP* publica **herramientas**
(funciones con nombre, descripción y esquema JSON de argumentos). Un *cliente MCP* (el agente) lista
esas herramientas, deja que el modelo elija cuál usar según la descripción y la invoca con
argumentos generados por el modelo. El servidor ejecuta la operación y devuelve el resultado o un
error legible.

Este proyecto usa el transporte **Streamable HTTP**: un único endpoint (`/mcp`) que recibe las
peticiones por `POST` y puede responder con JSON o con un flujo SSE.

## 6. Tecnologías y versiones

| Tecnología | Versión |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.4.7 |
| Spring AI (MCP Server WebMVC) | 1.1.4 (SDK MCP Java 0.17.0) |
| Spring Data JPA / Hibernate | gestionado por Spring Boot 3.4.7 (Hibernate 6.6.18) |
| Jakarta Bean Validation | gestionado por Spring Boot |
| MySQL Connector/J | gestionado por Spring Boot |
| MySQL Server | 8.0.19 o superior |
| H2 (solo pruebas) | gestionado por Spring Boot |
| Maven Wrapper | Maven 3.10.0 |

## 7. Estructura del proyecto

```
supermercado-mcp-server/
├── mvnw, mvnw.cmd, .mvn/wrapper/         Maven Wrapper
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/udea/supermercadomcp
    │   │   ├── SupermercadoMcpApplication.java
    │   │   ├── dto/          ProductoDto, StockDto, DetallePedidoDto, PedidoDto,
    │   │   │                 PedidoResumenDto, ResultadoPedidoDto
    │   │   ├── exception/    SupermercadoException y subclases
    │   │   ├── model/        Producto, Pedido, DetallePedido, EstadoPedido
    │   │   ├── repository/   ProductoRepository, PedidoRepository
    │   │   └── service/      SupermercadoToolsService, ValidacionesSupermercado
    │   └── resources
    │       ├── application.properties
    │       └── schema.sql
    └── test
        ├── java/com/udea/supermercadomcp
        │   ├── SupermercadoToolsServiceTest.java
        │   └── McpServidorIntegracionTest.java
        └── resources/application-test.properties
```

## 8. Modelo de datos

```
producto 1 ──────< detalle_pedido >────── 1 pedido
```

**producto**: `id` (PK), `sku` (único, inmutable), `nombre`, `categoria`, `precio DECIMAL(12,2) > 0`,
`stock INT >= 0`, `disponible`, `version` (bloqueo optimista).

**pedido**: `id` (PK), `cliente_nombre`, `telefono`, `direccion`, `fecha_creacion`,
`estado ∈ {BORRADOR, CONFIRMADO, CANCELADO}`, `total DECIMAL(14,2) >= 0`, `version`.

**detalle_pedido**: `id` (PK), `pedido_id` (FK → pedido, `ON DELETE CASCADE`),
`producto_id` (FK → producto, `ON DELETE RESTRICT`), `cantidad > 0`,
`precio_unitario > 0`, `subtotal > 0`, restricción única `(pedido_id, producto_id)`.

Decisiones de mapeo:

- `Pedido` es la raíz del agregado: `@OneToMany(mappedBy = "pedido", cascade = ALL, orphanRemoval = true)`.
  Los detalles solo se crean o eliminan a través del pedido.
- `DetallePedido → Producto` es unidireccional (`@ManyToOne(fetch = LAZY)`); el producto no conoce sus detalles.
- Todas las relaciones `@ManyToOne` son `LAZY`; las consultas que necesitan el grafo completo usan
  `@EntityGraph(attributePaths = {"detalles", "detalles.producto"})`, lo que evita N+1 y
  `LazyInitializationException` (además `spring.jpa.open-in-view=false`).
- Importes con `BigDecimal` (nunca `double`/`float`).
- `@Version` en `Producto` y `Pedido` para bloqueo optimista.
- `equals`/`hashCode`: `Producto` por SKU (clave natural); `Pedido` y `DetallePedido` por id con hash constante.
- Las herramientas devuelven DTO (records), no entidades: no hay ciclos JSON ni campos internos expuestos.

## 9. Herramientas MCP

| # | Herramienta | Parámetros | Descripción |
|---|-------------|-----------|-------------|
| 1 | `listarProductos` | — | Productos a la venta (disponibles y con stock > 0), ordenados por categoría y nombre. |
| 2 | `buscarProductos` | `texto` | Búsqueda parcial e insensible a mayúsculas por nombre, categoría o SKU. Rechaza texto vacío. |
| 3 | `consultarStock` | `productoId` | Datos del producto, existencias y si puede venderse. |
| 4 | `crearPedido` | `clienteNombre`, `telefono`, `direccion` | Crea un pedido `BORRADOR` con fecha actual y total 0. |
| 5 | `agregarProducto` | `pedidoId`, `productoId`, `cantidad` | Agrega unidades; si el producto ya está, incrementa la cantidad. Recalcula subtotal y total. |
| 6 | `retirarProducto` | `pedidoId`, `productoId` | Elimina la línea del producto y recalcula el total. |
| 7 | `consultarPedido` | `pedidoId` | Cliente, teléfono, dirección, fecha, estado, líneas y total. |
| 8 | `confirmarPedido` | `pedidoId` | Revalida stock, descuenta existencias y pasa a `CONFIRMADO` en una transacción. |
| 9 | `listarPedidos` | — | Todos los pedidos ordenados por id con estado, total y número de productos. |
| 10 | `cancelarPedido` | `pedidoId` | Cancela; si estaba `CONFIRMADO` restituye exactamente el stock. |

Las operaciones de escritura (`crearPedido`, `agregarProducto`, `retirarProducto`, `confirmarPedido`,
`cancelarPedido`) devuelven `{ "mensaje": "...", "pedido": { ... } }`.

## 10. Reglas de negocio

1. Solo un pedido en `BORRADOR` admite agregar o retirar productos.
2. La cantidad debe ser un entero mayor que cero.
3. Para agregar, el producto debe existir, estar marcado como disponible y tener stock para la
   **cantidad total** que quedaría en el pedido.
4. Agregar un producto ya presente incrementa la cantidad de su línea (no hay líneas duplicadas;
   también lo garantiza la restricción única en base de datos).
5. El detalle conserva el precio unitario vigente al agregarlo; cambios posteriores de precio no
   alteran el pedido.
6. En `BORRADOR` el stock no se reserva. Al confirmar, dentro de una sola transacción, se revalidan
   disponibilidad y stock de **todas** las líneas; si una falla, no se modifica nada.
7. Confirmar exige al menos una línea y no puede repetirse.
8. Cancelar un `BORRADOR` no toca el stock; cancelar un `CONFIRMADO` restituye exactamente las
   cantidades descontadas. No se puede cancelar dos veces ni confirmar un pedido cancelado.
9. Los conflictos de concurrencia (bloqueo optimista) se informan con un mensaje claro para reintentar.

Errores: cada violación lanza una excepción con mensaje determinista en español (por ejemplo,
`Stock insuficiente para 'Leche entera 1 L' (SKU LEC-001): se requieren 6 unidades y hay 5 disponibles.`).
El servidor MCP los devuelve al cliente como resultado de herramienta con `isError: true`.
No se devuelven `null` ni se ocultan excepciones.

## 11. Requisitos previos

- JDK 21 (`java -version`).
- MySQL Server 8.0.19 o superior en `localhost:3306` con el usuario `root`.
- No hace falta instalar Maven: se usa el wrapper (`mvnw.cmd` en Windows, `./mvnw` en Linux/macOS).

## 12. Crear la base de datos

El script `src/main/resources/schema.sql` crea la base `supermercado_mcp` (utf8mb4,
`utf8mb4_0900_ai_ci`), las tres tablas con claves, índices y restricciones `CHECK`, e inserta doce
productos sintéticos. Es **idempotente**: puede ejecutarse varias veces; los productos se actualizan
por SKU con `INSERT ... ON DUPLICATE KEY UPDATE` y el stock existente no se sobrescribe.

Desde la raíz del proyecto (PowerShell):

```powershell
mysql -u root -p -e "source src/main/resources/schema.sql"
```

La aplicación **no** ejecuta el script ni modifica el esquema al arrancar
(`spring.sql.init.mode=never`, `spring.jpa.hibernate.ddl-auto=none`).

## 13. Configurar la contraseña (`PWD_UDEA`)

La contraseña de MySQL nunca se escribe en el repositorio; `application.properties` la lee de la
variable de entorno `PWD_UDEA`. En PowerShell (el valor es solo un ejemplo):

```powershell
# Solo para la sesión actual de PowerShell
$env:PWD_UDEA = "cambia-esta-clave"

# Opcional: persistente para el usuario (abre una terminal nueva después)
[Environment]::SetEnvironmentVariable("PWD_UDEA", "cambia-esta-clave", "User")
```

## 14. Compilación

```powershell
.\mvnw.cmd clean package -DskipTests
```

Genera `target/supermercado-mcp-server-0.0.1-SNAPSHOT.jar`.

## 15. Ejecución

Con MySQL en marcha y `PWD_UDEA` definida:

```powershell
.\mvnw.cmd spring-boot:run
# o bien
java -jar target\supermercado-mcp-server-0.0.1-SNAPSHOT.jar
```

El endpoint MCP queda en **`http://localhost:8088/mcp`** (Streamable HTTP, servidor síncrono).
Los registros se escriben en `logs/supermercado-mcp-server.log` (ignorado por Git); no se registran
credenciales ni datos personales del cliente, solo identificadores de pedidos y SKU.

Configuración de ejemplo para un cliente MCP que admita Streamable HTTP:

```json
{
  "mcpServers": {
    "supermercado": { "url": "http://localhost:8088/mcp" }
  }
}
```

## 16. Pruebas

```powershell
.\mvnw.cmd clean test
```

Las pruebas usan el perfil `test` (`src/test/resources/application-test.properties`): H2 en memoria
con `MODE=MySQL`, esquema generado desde las entidades. **No necesitan MySQL ni `PWD_UDEA`.**

- `SupermercadoToolsServiceTest` (21 pruebas): reglas de negocio contra el estado persistido
  (creación, validaciones, cantidades, stock, incremento de cantidad, subtotales y total, precio
  conservado, retiro, confirmación y su atomicidad, confirmación y cancelación repetidas,
  restitución de stock, búsqueda, listados).
- `McpServidorIntegracionTest` (3 pruebas): protocolo MCP sobre el endpoint real `/mcp`
  (`initialize` → `notifications/initialized` → `tools/list` → `tools/call`): exactamente diez
  herramientas con esquema, flujo completo y errores con `isError`. Usa `MockMvc` para que el
  resultado no dependa del software que intercepte tráfico HTTP local.

## 17. Ejemplos de solicitudes al chatbot

- "¿Qué productos tienen disponibles?" → `listarProductos`
- "¿Tienen café? ¿Y cosas de aseo?" → `buscarProductos`
- "¿Cuántas unidades de leche quedan?" → `buscarProductos` + `consultarStock`
- "Quiero hacer un pedido a nombre de Ana Gómez, teléfono 300 000 0000, Calle 1 # 2-3." → `crearPedido`
- "Agrégale 3 arroces y 2 leches." → `agregarProducto` (dos llamadas)
- "Mejor quita la leche." → `retirarProducto`
- "¿Cuánto va mi pedido?" → `consultarPedido`
- "Confírmalo." → `confirmarPedido`
- "Muéstrame todos los pedidos." → `listarPedidos`
- "Cancela el pedido 4." → `cancelarPedido`

## 18. Seguridad y manejo de secretos

- Ninguna contraseña, clave o token se versiona; la contraseña de MySQL se inyecta con `${PWD_UDEA}`.
- `.gitignore` excluye `target/`, logs, archivos de IDE, archivos `.env` y temporales.
- Las respuestas MCP usan DTO: no exponen versiones internas ni relaciones JPA.
- Todos los argumentos se validan antes de tocar la base de datos.
- Los datos semilla son sintéticos; no hay datos personales reales.
- El servidor no implementa autenticación: está pensado para ejecutarse en `localhost` durante el laboratorio.

## 19. Limitaciones

- Sin autenticación ni autorización; no debe exponerse a redes públicas.
- El stock no se reserva mientras el pedido está en `BORRADOR`; se valida al agregar y de nuevo al confirmar.
- `retirarProducto` elimina la línea completa; para reducir unidades hay que retirar y volver a agregar.
- Concurrencia resuelta con bloqueo optimista: ante un conflicto se pide reintentar, no se reintenta automáticamente.
- El esquema MySQL lo gobierna `schema.sql`; las pruebas validan el mapeo sobre H2 (modo MySQL), no
  sobre un MySQL real.
- Algunos antivirus con inspección web (p. ej. escudos HTTP) pueden interceptar el tráfico a
  `localhost` y alterar las respuestas `202` del protocolo MCP, lo que hace que ciertos clientes
  se queden esperando. Si ocurre, excluya `localhost:8088` de la inspección del antivirus.

## 20. Atribución

Este proyecto parte del ejemplo de gestión de tareas suministrado por el profesor en
[diegobotia/labmcp](https://github.com/diegobotia/labmcp). Del original se conservan la estructura
de Spring Boot + Spring AI MCP Server, el Maven Wrapper y el patrón de registro con
`MethodToolCallbackProvider`; el dominio de tareas se reemplazó completamente por el de pedidos de
supermercado.

## 21. Autoría

**Maria Camila Castañeda Piedrahita** — Arquitectura de Software, Universidad de Antioquia.

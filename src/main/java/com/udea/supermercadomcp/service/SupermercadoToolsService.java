package com.udea.supermercadomcp.service;

import com.udea.supermercadomcp.dto.PedidoDto;
import com.udea.supermercadomcp.dto.PedidoResumenDto;
import com.udea.supermercadomcp.dto.ProductoDto;
import com.udea.supermercadomcp.dto.ResultadoPedidoDto;
import com.udea.supermercadomcp.dto.StockDto;
import com.udea.supermercadomcp.exception.RecursoNoEncontradoException;
import com.udea.supermercadomcp.exception.ReglaNegocioException;
import com.udea.supermercadomcp.model.DetallePedido;
import com.udea.supermercadomcp.model.EstadoPedido;
import com.udea.supermercadomcp.model.Pedido;
import com.udea.supermercadomcp.model.Producto;
import com.udea.supermercadomcp.repository.PedidoRepository;
import com.udea.supermercadomcp.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.udea.supermercadomcp.service.ValidacionesSupermercado.MAX_CLIENTE_NOMBRE;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.MAX_DIRECCION;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.MAX_TEXTO_BUSQUEDA;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirBorrador;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirCancelable;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirCantidadPositiva;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirConfirmable;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirDisponible;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirId;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirStockSuficiente;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirTelefono;
import static com.udea.supermercadomcp.service.ValidacionesSupermercado.exigirTexto;

/**
 * Gestión de pedidos de supermercado a domicilio expuesta como herramientas MCP.
 *
 * Cada método @Tool se publica en el servidor MCP (registrado con MethodToolCallbackProvider):
 *   - nombre      = atributo name
 *   - descripción = atributo description, usada por el modelo para elegir la herramienta
 *   - JSON schema = derivado de los parámetros y sus @ToolParam
 *
 * Las herramientas devuelven DTO (records), nunca entidades JPA. Los errores se lanzan como
 * excepciones de negocio cuyo mensaje llega al cliente MCP como resultado con error.
 * Por defecto las transacciones son de solo lectura; las operaciones de escritura las redefinen.
 */
@Component
@Transactional(readOnly = true)
public class SupermercadoToolsService {

    private static final Logger log = LoggerFactory.getLogger(SupermercadoToolsService.class);

    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;

    public SupermercadoToolsService(ProductoRepository productoRepository, PedidoRepository pedidoRepository) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    // ---------- Productos ----------

    @Tool(name = "listarProductos",
            description = "Lista los productos del supermercado que están a la venta (marcados como disponibles y con "
                    + "stock mayor que cero), ordenados por categoría y nombre. Devuelve id, SKU, nombre, categoría, "
                    + "precio en pesos colombianos y stock. Úsala para mostrar el catálogo o para obtener el id de un "
                    + "producto antes de agregarlo a un pedido.")
    public List<ProductoDto> listarProductos() {
        return productoRepository.findDisponibles().stream().map(ProductoDto::desde).toList();
    }

    @Tool(name = "buscarProductos",
            description = "Busca productos cuyo nombre, categoría o SKU contenga el texto indicado, sin distinguir "
                    + "mayúsculas de minúsculas. Incluye productos no disponibles (ver el campo 'disponible'). "
                    + "Devuelve una lista vacía si no hay coincidencias.")
    public List<ProductoDto> buscarProductos(
            @ToolParam(description = "Texto a buscar, por ejemplo 'arroz', 'lacteos' o 'ARR-001'. Obligatorio, no vacío.")
            String texto) {
        String criterio = exigirTexto(texto, "texto de búsqueda", MAX_TEXTO_BUSQUEDA);
        return productoRepository.buscarPorTexto(criterio).stream().map(ProductoDto::desde).toList();
    }

    @Tool(name = "consultarStock",
            description = "Consulta un producto por su id y devuelve sus datos, las unidades en existencia y si "
                    + "puede venderse en este momento.")
    public StockDto consultarStock(
            @ToolParam(description = "Id numérico del producto (entero positivo), obtenido de listarProductos o buscarProductos.")
            Long productoId) {
        Producto producto = buscarProducto(productoId);
        boolean aLaVenta = producto.estaALaVenta();
        String mensaje;
        if (!Boolean.TRUE.equals(producto.getDisponible())) {
            mensaje = String.format("El producto '%s' no está disponible para la venta.", producto.getNombre());
        } else if (!aLaVenta) {
            mensaje = String.format("El producto '%s' está agotado.", producto.getNombre());
        } else {
            mensaje = String.format("Hay %d unidades de '%s' en existencia.", producto.getStock(), producto.getNombre());
        }
        return new StockDto(ProductoDto.desde(producto), aLaVenta, mensaje);
    }

    // ---------- Pedidos ----------

    @Tool(name = "crearPedido",
            description = "Crea un pedido a domicilio nuevo en estado BORRADOR, con la fecha actual y total cero. "
                    + "Requiere el nombre del cliente, su teléfono y la dirección de entrega. Devuelve el pedido "
                    + "creado con su id, que se usa después para agregar productos y confirmarlo.")
    @Transactional
    public ResultadoPedidoDto crearPedido(
            @ToolParam(description = "Nombre completo del cliente que recibe el pedido. Obligatorio, máximo 120 caracteres.")
            String clienteNombre,
            @ToolParam(description = "Teléfono de contacto del cliente: dígitos y opcionalmente '+', '-', espacios o paréntesis; al menos 7 dígitos.")
            String telefono,
            @ToolParam(description = "Dirección de entrega del pedido. Obligatoria, máximo 200 caracteres.")
            String direccion) {
        Pedido pedido = new Pedido(
                exigirTexto(clienteNombre, "clienteNombre", MAX_CLIENTE_NOMBRE),
                exigirTelefono(telefono),
                exigirTexto(direccion, "direccion", MAX_DIRECCION),
                LocalDateTime.now());
        pedidoRepository.saveAndFlush(pedido);
        log.info("Pedido {} creado en estado {}", pedido.getId(), pedido.getEstado());
        return new ResultadoPedidoDto(
                String.format("Pedido %d creado en estado BORRADOR.", pedido.getId()),
                PedidoDto.desde(pedido));
    }

    @Tool(name = "agregarProducto",
            description = "Agrega unidades de un producto a un pedido en estado BORRADOR. Si el producto ya estaba en "
                    + "el pedido, incrementa su cantidad en lugar de crear otra línea. Valida que el producto esté "
                    + "disponible y que el stock alcance para la cantidad total pedida. Conserva el precio unitario "
                    + "vigente y recalcula subtotal y total provisional. No descuenta stock: eso ocurre al confirmar.")
    @Transactional
    public ResultadoPedidoDto agregarProducto(
            @ToolParam(description = "Id numérico del pedido (entero positivo) en estado BORRADOR.")
            Long pedidoId,
            @ToolParam(description = "Id numérico del producto (entero positivo) que se desea agregar.")
            Long productoId,
            @ToolParam(description = "Número de unidades a agregar; entero mayor que cero.")
            Integer cantidad) {
        int unidades = exigirCantidadPositiva(cantidad);
        Pedido pedido = buscarPedido(pedidoId);
        exigirBorrador(pedido, "modificar");
        Producto producto = buscarProducto(productoId);
        exigirDisponible(producto);

        DetallePedido detalle = pedido.buscarDetalle(producto.getId()).orElse(null);
        long cantidadTotal = (detalle == null ? 0L : detalle.getCantidad()) + (long) unidades;
        exigirStockSuficiente(producto, cantidadTotal);

        if (detalle == null) {
            pedido.agregarDetalle(new DetallePedido(producto, unidades));
        } else {
            detalle.incrementarCantidad(unidades);
        }
        pedido.recalcularTotal();
        sincronizar(pedido.getId());

        log.info("Pedido {}: +{} unidades del producto {}", pedido.getId(), unidades, producto.getSku());
        return new ResultadoPedidoDto(
                String.format("Se agregaron %d unidades de '%s' al pedido %d. Cantidad en el pedido: %d. Total provisional: %s.",
                        unidades, producto.getNombre(), pedido.getId(), cantidadTotal, pedido.getTotal().toPlainString()),
                PedidoDto.desde(pedido));
    }

    @Tool(name = "retirarProducto",
            description = "Retira por completo un producto (todas sus unidades) de un pedido en estado BORRADOR y "
                    + "recalcula el total. Informa un error si el producto no estaba agregado al pedido.")
    @Transactional
    public ResultadoPedidoDto retirarProducto(
            @ToolParam(description = "Id numérico del pedido (entero positivo) en estado BORRADOR.")
            Long pedidoId,
            @ToolParam(description = "Id numérico del producto (entero positivo) que se desea retirar del pedido.")
            Long productoId) {
        Pedido pedido = buscarPedido(pedidoId);
        exigirId(productoId, "productoId");
        exigirBorrador(pedido, "modificar");
        DetallePedido detalle = pedido.buscarDetalle(productoId)
                .orElseThrow(() -> new ReglaNegocioException(String.format(
                        "El producto %d no está agregado al pedido %d; no hay nada que retirar.", productoId, pedidoId)));

        String nombreProducto = detalle.getProducto().getNombre();
        pedido.retirarDetalle(detalle);
        pedido.recalcularTotal();
        sincronizar(pedido.getId());

        log.info("Pedido {}: producto {} retirado", pedido.getId(), productoId);
        return new ResultadoPedidoDto(
                String.format("Se retiró '%s' del pedido %d. Total provisional: %s.",
                        nombreProducto, pedido.getId(), pedido.getTotal().toPlainString()),
                PedidoDto.desde(pedido));
    }

    @Tool(name = "consultarPedido",
            description = "Consulta un pedido por su id y devuelve cliente, teléfono, dirección, fecha de creación, "
                    + "estado, las líneas con producto, cantidad, precio unitario y subtotal, y el total.")
    public PedidoDto consultarPedido(
            @ToolParam(description = "Id numérico del pedido (entero positivo).")
            Long pedidoId) {
        return PedidoDto.desde(buscarPedido(pedidoId));
    }

    @Tool(name = "confirmarPedido",
            description = "Confirma un pedido en estado BORRADOR que tenga al menos un producto. En una única "
                    + "transacción vuelve a validar disponibilidad y stock de todos los productos, descuenta las "
                    + "existencias, recalcula el total definitivo y cambia el estado a CONFIRMADO. Si alguna "
                    + "validación falla no se modifica nada. Un pedido no puede confirmarse dos veces.")
    @Transactional
    public ResultadoPedidoDto confirmarPedido(
            @ToolParam(description = "Id numérico del pedido (entero positivo) que se desea confirmar.")
            Long pedidoId) {
        Pedido pedido = buscarPedido(pedidoId);
        exigirConfirmable(pedido);

        // Primero se valida todo y luego se descuenta, para no dejar cambios parciales en memoria.
        for (DetallePedido detalle : pedido.getDetalles()) {
            exigirDisponible(detalle.getProducto());
            exigirStockSuficiente(detalle.getProducto(), detalle.getCantidad());
        }
        for (DetallePedido detalle : pedido.getDetalles()) {
            detalle.getProducto().descontarStock(detalle.getCantidad());
        }
        pedido.recalcularTotal();
        pedido.marcarConfirmado();
        sincronizar(pedido.getId());

        log.info("Pedido {} confirmado con total {}", pedido.getId(), pedido.getTotal());
        return new ResultadoPedidoDto(
                String.format("Pedido %d confirmado. Total definitivo: %s.",
                        pedido.getId(), pedido.getTotal().toPlainString()),
                PedidoDto.desde(pedido));
    }

    @Tool(name = "listarPedidos",
            description = "Lista todos los pedidos registrados, ordenados por id, con cliente, fecha de creación, "
                    + "estado (BORRADOR, CONFIRMADO o CANCELADO), total y número de productos distintos.")
    public List<PedidoResumenDto> listarPedidos() {
        return pedidoRepository.listarResumenes();
    }

    @Tool(name = "cancelarPedido",
            description = "Cancela un pedido. Si estaba en BORRADOR solo cambia su estado; si estaba CONFIRMADO "
                    + "restituye exactamente el stock de cada producto. Todo ocurre en una transacción. Un pedido "
                    + "ya cancelado no puede cancelarse de nuevo.")
    @Transactional
    public ResultadoPedidoDto cancelarPedido(
            @ToolParam(description = "Id numérico del pedido (entero positivo) que se desea cancelar.")
            Long pedidoId) {
        Pedido pedido = buscarPedido(pedidoId);
        exigirCancelable(pedido);

        EstadoPedido estadoAnterior = pedido.getEstado();
        if (estadoAnterior == EstadoPedido.CONFIRMADO) {
            for (DetallePedido detalle : pedido.getDetalles()) {
                detalle.getProducto().restituirStock(detalle.getCantidad());
            }
        }
        pedido.marcarCancelado();
        sincronizar(pedido.getId());

        log.info("Pedido {} cancelado (estado anterior {})", pedido.getId(), estadoAnterior);
        String mensaje = estadoAnterior == EstadoPedido.CONFIRMADO
                ? String.format("Pedido %d cancelado. Se restituyó el stock de sus productos.", pedido.getId())
                : String.format("Pedido %d cancelado. Estaba en BORRADOR, por lo que el stock no cambió.", pedido.getId());
        return new ResultadoPedidoDto(mensaje, PedidoDto.desde(pedido));
    }

    // ---------- Auxiliares ----------

    private Producto buscarProducto(Long productoId) {
        exigirId(productoId, "productoId");
        return productoRepository.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        String.format("No existe un producto con id %d.", productoId)));
    }

    private Pedido buscarPedido(Long pedidoId) {
        exigirId(pedidoId, "pedidoId");
        return pedidoRepository.findConDetallesById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        String.format("No existe un pedido con id %d.", pedidoId)));
    }

    /**
     * Fuerza la escritura dentro del método para traducir los conflictos de concurrencia
     * (versión obsoleta o línea duplicada) a un mensaje claro en lugar de fallar al hacer commit.
     */
    private void sincronizar(Long pedidoId) {
        try {
            pedidoRepository.flush();
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new ReglaNegocioException(String.format(
                    "El pedido %d o alguno de sus productos fue modificado por otra operación al mismo tiempo; "
                            + "consulte el pedido y vuelva a intentarlo.", pedidoId), e);
        }
    }
}

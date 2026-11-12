package com.udea.supermercadomcp;

import com.udea.supermercadomcp.dto.DetallePedidoDto;
import com.udea.supermercadomcp.dto.PedidoDto;
import com.udea.supermercadomcp.dto.PedidoResumenDto;
import com.udea.supermercadomcp.dto.ProductoDto;
import com.udea.supermercadomcp.dto.ResultadoPedidoDto;
import com.udea.supermercadomcp.dto.StockDto;
import com.udea.supermercadomcp.exception.DatoInvalidoException;
import com.udea.supermercadomcp.exception.RecursoNoEncontradoException;
import com.udea.supermercadomcp.exception.ReglaNegocioException;
import com.udea.supermercadomcp.model.DetallePedido;
import com.udea.supermercadomcp.model.EstadoPedido;
import com.udea.supermercadomcp.model.Pedido;
import com.udea.supermercadomcp.model.Producto;
import com.udea.supermercadomcp.repository.PedidoRepository;
import com.udea.supermercadomcp.repository.ProductoRepository;
import com.udea.supermercadomcp.service.SupermercadoToolsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del servicio de herramientas contra H2 (perfil "test").
 * No usan @Transactional: cada herramienta confirma su propia transacción y las
 * verificaciones releen el estado persistido desde los repositorios.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupermercadoToolsServiceTest {

    private static final String CLIENTE = "Cliente de Prueba";
    private static final String TELEFONO = "300 123 4567";
    private static final String DIRECCION = "Calle 10 # 20-30, Medellín";

    @Autowired
    private SupermercadoToolsService tools;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private Producto arroz;
    private Producto leche;
    private Producto papel;

    @BeforeEach
    void prepararDatos() {
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
        arroz = productoRepository.save(
                new Producto("ARR-001", "Arroz blanco 1 kg", "Granos", new BigDecimal("4900.00"), 10, true));
        leche = productoRepository.save(
                new Producto("LEC-001", "Leche entera 1 L", "Lácteos", new BigDecimal("4200.00"), 5, true));
        papel = productoRepository.save(
                new Producto("PAP-012", "Papel higiénico x 12", "Aseo", new BigDecimal("21500.00"), 20, false));
    }

    // ---------- Creación de pedidos ----------

    @Test
    @DisplayName("1. crearPedido crea un pedido BORRADOR con fecha actual y total cero")
    void crearPedidoCorrecto() {
        LocalDateTime antes = LocalDateTime.now();
        ResultadoPedidoDto resultado = tools.crearPedido("  Ana Gómez  ", TELEFONO, DIRECCION);
        LocalDateTime despues = LocalDateTime.now();

        PedidoDto dto = resultado.pedido();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.estado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(dto.total()).isEqualByComparingTo("0");
        assertThat(dto.detalles()).isEmpty();
        assertThat(resultado.mensaje()).contains("Pedido " + dto.id() + " creado");

        Pedido persistido = pedidoPersistido(dto.id());
        assertThat(persistido.getClienteNombre()).isEqualTo("Ana Gómez");
        assertThat(persistido.getTelefono()).isEqualTo(TELEFONO);
        assertThat(persistido.getDireccion()).isEqualTo(DIRECCION);
        assertThat(persistido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(persistido.getTotal()).isEqualByComparingTo("0");
        assertThat(persistido.getFechaCreacion()).isBetween(antes.minusSeconds(1), despues.plusSeconds(1));
    }

    @Test
    @DisplayName("2. crearPedido rechaza datos obligatorios vacíos o inválidos")
    void crearPedidoRechazaDatosVacios() {
        assertThatThrownBy(() -> tools.crearPedido("   ", TELEFONO, DIRECCION))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El campo 'clienteNombre' es obligatorio y no puede estar vacío.");
        assertThatThrownBy(() -> tools.crearPedido(CLIENTE, null, DIRECCION))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El campo 'telefono' es obligatorio y no puede estar vacío.");
        assertThatThrownBy(() -> tools.crearPedido(CLIENTE, TELEFONO, ""))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El campo 'direccion' es obligatorio y no puede estar vacío.");
        assertThatThrownBy(() -> tools.crearPedido(CLIENTE, "llámame", DIRECCION))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessageContaining("'telefono' no es válido");

        assertThat(pedidoRepository.count()).isZero();
    }

    // ---------- Agregar productos ----------

    @Test
    @DisplayName("3. agregarProducto rechaza cantidades cero, negativas o nulas")
    void agregarProductoRechazaCantidadNoPositiva() {
        Long pedidoId = nuevoPedido();

        for (Integer cantidad : new Integer[]{0, -3, null}) {
            assertThatThrownBy(() -> tools.agregarProducto(pedidoId, arroz.getId(), cantidad))
                    .isInstanceOf(DatoInvalidoException.class)
                    .hasMessage("La cantidad debe ser un número entero mayor que cero; se recibió: " + cantidad + ".");
        }

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getDetalles()).isEmpty();
        assertThat(persistido.getTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("4. agregarProducto rechaza stock insuficiente, contando lo ya agregado")
    void agregarProductoRechazaStockInsuficiente() {
        Long pedidoId = nuevoPedido();

        assertThatThrownBy(() -> tools.agregarProducto(pedidoId, leche.getId(), 6))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Stock insuficiente para 'Leche entera 1 L' (SKU LEC-001): se requieren 6 unidades y hay 5 disponibles.");

        tools.agregarProducto(pedidoId, leche.getId(), 3);
        assertThatThrownBy(() -> tools.agregarProducto(pedidoId, leche.getId(), 3))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("se requieren 6 unidades y hay 5 disponibles");

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getDetalles()).singleElement()
                .extracting(DetallePedido::getCantidad).isEqualTo(3);
        assertThat(stockActual(leche)).isEqualTo(5);
    }

    @Test
    @DisplayName("5. agregar dos veces el mismo producto incrementa la cantidad en una sola línea")
    void agregarMismoProductoIncrementaCantidad() {
        Long pedidoId = nuevoPedido();

        tools.agregarProducto(pedidoId, arroz.getId(), 2);
        ResultadoPedidoDto resultado = tools.agregarProducto(pedidoId, arroz.getId(), 3);

        assertThat(resultado.pedido().detalles()).singleElement()
                .satisfies(d -> {
                    assertThat(d.cantidad()).isEqualTo(5);
                    assertThat(d.subtotal()).isEqualByComparingTo("24500.00");
                });
        assertThat(resultado.mensaje()).contains("Cantidad en el pedido: 5");

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getDetalles()).hasSize(1);
        assertThat(persistido.getDetalles().get(0).getCantidad()).isEqualTo(5);
        assertThat(persistido.getTotal()).isEqualByComparingTo("24500.00");
    }

    @Test
    @DisplayName("6. subtotales y total se calculan con precisión decimal")
    void calculaSubtotalesYTotal() {
        Long pedidoId = nuevoPedido();

        tools.agregarProducto(pedidoId, arroz.getId(), 3);   // 3 x 4900 = 14700
        PedidoDto dto = tools.agregarProducto(pedidoId, leche.getId(), 2).pedido();   // 2 x 4200 = 8400

        assertThat(dto.detalles()).extracting(DetallePedidoDto::sku).containsExactly("ARR-001", "LEC-001");
        assertThat(dto.detalles().get(0).precioUnitario()).isEqualByComparingTo("4900.00");
        assertThat(dto.detalles().get(0).subtotal()).isEqualByComparingTo("14700.00");
        assertThat(dto.detalles().get(1).subtotal()).isEqualByComparingTo("8400.00");
        assertThat(dto.total()).isEqualByComparingTo("23100.00");

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getTotal()).isEqualByComparingTo("23100.00");
        assertThat(persistido.getDetalles()).extracting(DetallePedido::getSubtotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("14700.00"), new BigDecimal("8400.00"));
    }

    @Test
    @DisplayName("el detalle conserva el precio unitario aunque luego cambie el precio del producto")
    void conservaPrecioUnitario() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 2);

        Producto cambiado = productoRepository.findById(arroz.getId()).orElseThrow();
        cambiado.setPrecio(new BigDecimal("5500.00"));
        productoRepository.save(cambiado);

        PedidoDto confirmado = tools.confirmarPedido(pedidoId).pedido();
        assertThat(confirmado.detalles().get(0).precioUnitario()).isEqualByComparingTo("4900.00");
        assertThat(confirmado.total()).isEqualByComparingTo("9800.00");
    }

    @Test
    @DisplayName("agregarProducto rechaza productos no disponibles o inexistentes y pedidos inexistentes")
    void agregarProductoRechazaProductoNoDisponibleOInexistente() {
        Long pedidoId = nuevoPedido();

        assertThatThrownBy(() -> tools.agregarProducto(pedidoId, papel.getId(), 1))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El producto 'Papel higiénico x 12' (SKU PAP-012) no está disponible para la venta.");
        assertThatThrownBy(() -> tools.agregarProducto(pedidoId, 999_999L, 1))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe un producto con id 999999.");
        assertThatThrownBy(() -> tools.agregarProducto(999_999L, arroz.getId(), 1))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe un pedido con id 999999.");
        assertThatThrownBy(() -> tools.agregarProducto(0L, arroz.getId(), 1))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El parámetro 'pedidoId' es obligatorio y debe ser un número entero positivo; se recibió: 0.");
        assertThatThrownBy(() -> tools.consultarPedido(null))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessageContaining("'pedidoId' es obligatorio");

        assertThat(pedidoPersistido(pedidoId).getDetalles()).isEmpty();
    }

    // ---------- Retirar productos ----------

    @Test
    @DisplayName("retirarProducto elimina la línea, recalcula el total e informa si el producto no estaba")
    void retirarProducto() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 1);
        tools.agregarProducto(pedidoId, leche.getId(), 2);

        ResultadoPedidoDto resultado = tools.retirarProducto(pedidoId, leche.getId());
        assertThat(resultado.pedido().detalles()).extracting(DetallePedidoDto::sku).containsExactly("ARR-001");
        assertThat(resultado.pedido().total()).isEqualByComparingTo("4900.00");

        assertThatThrownBy(() -> tools.retirarProducto(pedidoId, leche.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El producto " + leche.getId() + " no está agregado al pedido " + pedidoId
                        + "; no hay nada que retirar.");

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getDetalles()).hasSize(1);
        assertThat(persistido.getTotal()).isEqualByComparingTo("4900.00");
    }

    // ---------- Confirmación ----------

    @Test
    @DisplayName("7. confirmarPedido descuenta el stock y deja el pedido CONFIRMADO")
    void confirmarPedidoDescuentaStock() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 4);
        tools.agregarProducto(pedidoId, leche.getId(), 5);

        ResultadoPedidoDto resultado = tools.confirmarPedido(pedidoId);

        assertThat(resultado.pedido().estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(resultado.mensaje()).isEqualTo("Pedido " + pedidoId + " confirmado. Total definitivo: 40600.00.");
        assertThat(stockActual(arroz)).isEqualTo(6);
        assertThat(stockActual(leche)).isZero();
        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(persistido.getTotal()).isEqualByComparingTo("40600.00");
    }

    @Test
    @DisplayName("8. confirmarPedido rechaza un pedido sin productos")
    void confirmarPedidoSinProductos() {
        Long pedidoId = nuevoPedido();

        assertThatThrownBy(() -> tools.confirmarPedido(pedidoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El pedido " + pedidoId + " no tiene productos; agregue al menos uno antes de confirmarlo.");
        assertThat(pedidoPersistido(pedidoId).getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("confirmarPedido rechaza la confirmación repetida sin descontar stock dos veces")
    void confirmarPedidoRepetido() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 2);
        tools.confirmarPedido(pedidoId);

        assertThatThrownBy(() -> tools.confirmarPedido(pedidoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El pedido " + pedidoId + " ya fue confirmado; no se puede confirmar nuevamente.");
        assertThat(stockActual(arroz)).isEqualTo(8);
    }

    @Test
    @DisplayName("confirmarPedido revalida el stock y no deja cambios parciales si falla")
    void confirmarPedidoEsAtomico() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 4);
        tools.agregarProducto(pedidoId, leche.getId(), 5);

        // Otro canal de venta reduce el stock de leche después de armar el pedido.
        Producto lecheActual = productoRepository.findById(leche.getId()).orElseThrow();
        lecheActual.setStock(2);
        productoRepository.save(lecheActual);

        assertThatThrownBy(() -> tools.confirmarPedido(pedidoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Stock insuficiente para 'Leche entera 1 L' (SKU LEC-001): se requieren 5 unidades y hay 2 disponibles.");

        assertThat(stockActual(arroz)).isEqualTo(10);
        assertThat(stockActual(leche)).isEqualTo(2);
        assertThat(pedidoPersistido(pedidoId).getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("9. un pedido confirmado no admite agregar ni retirar productos")
    void rechazaModificacionDespuesDeConfirmar() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 2);
        tools.confirmarPedido(pedidoId);
        String esperado = "El pedido " + pedidoId
                + " está en estado CONFIRMADO; solo se puede modificar un pedido en estado BORRADOR.";

        assertThatThrownBy(() -> tools.agregarProducto(pedidoId, leche.getId(), 1))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(esperado);
        assertThatThrownBy(() -> tools.retirarProducto(pedidoId, arroz.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(esperado);

        Pedido persistido = pedidoPersistido(pedidoId);
        assertThat(persistido.getDetalles()).singleElement()
                .extracting(DetallePedido::getCantidad).isEqualTo(2);
        assertThat(stockActual(arroz)).isEqualTo(8);
        assertThat(stockActual(leche)).isEqualTo(5);
    }

    // ---------- Cancelación ----------

    @Test
    @DisplayName("10. cancelar un pedido confirmado restituye exactamente el stock")
    void cancelarPedidoConfirmadoRestituyeStock() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 4);
        tools.agregarProducto(pedidoId, leche.getId(), 2);
        tools.confirmarPedido(pedidoId);
        assertThat(stockActual(arroz)).isEqualTo(6);
        assertThat(stockActual(leche)).isEqualTo(3);

        ResultadoPedidoDto resultado = tools.cancelarPedido(pedidoId);

        assertThat(resultado.mensaje()).contains("Se restituyó el stock");
        assertThat(stockActual(arroz)).isEqualTo(10);
        assertThat(stockActual(leche)).isEqualTo(5);
        assertThat(pedidoPersistido(pedidoId).getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("cancelar un pedido en BORRADOR no modifica el stock")
    void cancelarPedidoBorradorNoCambiaStock() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 4);

        ResultadoPedidoDto resultado = tools.cancelarPedido(pedidoId);

        assertThat(resultado.mensaje()).contains("el stock no cambió");
        assertThat(stockActual(arroz)).isEqualTo(10);
        assertThat(pedidoPersistido(pedidoId).getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("11. cancelarPedido rechaza la cancelación repetida sin restituir stock dos veces")
    void cancelarPedidoRepetido() {
        Long pedidoId = nuevoPedido();
        tools.agregarProducto(pedidoId, arroz.getId(), 3);
        tools.confirmarPedido(pedidoId);
        tools.cancelarPedido(pedidoId);

        assertThatThrownBy(() -> tools.cancelarPedido(pedidoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El pedido " + pedidoId + " ya está cancelado; no se puede cancelar nuevamente.");
        assertThatThrownBy(() -> tools.confirmarPedido(pedidoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("está en estado CANCELADO");
        assertThat(stockActual(arroz)).isEqualTo(10);
    }

    // ---------- Consultas ----------

    @Test
    @DisplayName("12. buscarProductos busca por nombre, categoría o SKU sin distinguir mayúsculas")
    void buscarProductos() {
        assertThat(tools.buscarProductos("ARROZ")).extracting(ProductoDto::sku).containsExactly("ARR-001");
        assertThat(tools.buscarProductos("lácteos")).extracting(ProductoDto::sku).containsExactly("LEC-001");
        assertThat(tools.buscarProductos("pap-0")).extracting(ProductoDto::sku).containsExactly("PAP-012");
        assertThat(tools.buscarProductos("  leche ")).extracting(ProductoDto::nombre).containsExactly("Leche entera 1 L");
        assertThat(tools.buscarProductos("inexistente")).isEmpty();

        assertThatThrownBy(() -> tools.buscarProductos("   "))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El campo 'texto de búsqueda' es obligatorio y no puede estar vacío.");
        assertThatThrownBy(() -> tools.buscarProductos(null))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test
    @DisplayName("listarProductos devuelve solo productos disponibles y con existencias")
    void listarProductosSoloDisponibles() {
        productoRepository.save(new Producto("AZU-001", "Azúcar blanca 1 kg", "Despensa", new BigDecimal("4500.00"), 0, true));

        List<ProductoDto> productos = tools.listarProductos();

        assertThat(productos).extracting(ProductoDto::sku).containsExactly("ARR-001", "LEC-001");
        assertThat(productos.get(0).precio()).isEqualByComparingTo("4900.00");
        assertThat(productos.get(0).stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("consultarStock informa existencias y disponibilidad")
    void consultarStock() {
        StockDto stock = tools.consultarStock(leche.getId());
        assertThat(stock.producto().stock()).isEqualTo(5);
        assertThat(stock.aLaVenta()).isTrue();
        assertThat(stock.mensaje()).isEqualTo("Hay 5 unidades de 'Leche entera 1 L' en existencia.");

        StockDto noDisponible = tools.consultarStock(papel.getId());
        assertThat(noDisponible.aLaVenta()).isFalse();
        assertThat(noDisponible.mensaje()).contains("no está disponible");

        assertThatThrownBy(() -> tools.consultarStock(-1L))
                .isInstanceOf(DatoInvalidoException.class)
                .hasMessage("El parámetro 'productoId' es obligatorio y debe ser un número entero positivo; se recibió: -1.");
    }

    @Test
    @DisplayName("consultarPedido y listarPedidos devuelven el estado completo y ordenado")
    void consultarYListarPedidos() {
        Long primero = nuevoPedido();
        tools.agregarProducto(primero, arroz.getId(), 1);
        tools.agregarProducto(primero, leche.getId(), 1);
        tools.confirmarPedido(primero);
        Long segundo = nuevoPedido();

        PedidoDto consultado = tools.consultarPedido(primero);
        assertThat(consultado.clienteNombre()).isEqualTo(CLIENTE);
        assertThat(consultado.telefono()).isEqualTo(TELEFONO);
        assertThat(consultado.direccion()).isEqualTo(DIRECCION);
        assertThat(consultado.fechaCreacion()).isNotNull();
        assertThat(consultado.detalles()).hasSize(2);
        assertThat(consultado.total()).isEqualByComparingTo("9100.00");

        List<PedidoResumenDto> pedidos = tools.listarPedidos();
        assertThat(pedidos).extracting(PedidoResumenDto::id).containsExactly(primero, segundo);
        assertThat(pedidos.get(0).estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(pedidos.get(0).total()).isEqualByComparingTo("9100.00");
        assertThat(pedidos.get(0).cantidadProductos()).isEqualTo(2L);
        assertThat(pedidos.get(1).estado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedidos.get(1).cantidadProductos()).isZero();
    }

    // ---------- Auxiliares ----------

    private Long nuevoPedido() {
        return tools.crearPedido(CLIENTE, TELEFONO, DIRECCION).pedido().id();
    }

    private Pedido pedidoPersistido(Long id) {
        return pedidoRepository.findConDetallesById(id).orElseThrow();
    }

    private int stockActual(Producto producto) {
        return productoRepository.findById(producto.getId()).orElseThrow().getStock();
    }
}

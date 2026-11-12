package com.udea.supermercadomcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udea.supermercadomcp.model.Producto;
import com.udea.supermercadomcp.repository.PedidoRepository;
import com.udea.supermercadomcp.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Prueba del protocolo MCP sobre el endpoint real /mcp (Streamable HTTP):
 * initialize -> notifications/initialized -> tools/list -> tools/call.
 *
 * Se usa MockMvc en lugar de un socket para que el resultado no dependa de software
 * que intercepte el tráfico HTTP local (por ejemplo, escudos web de antivirus).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpServidorIntegracionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACCEPT_MCP = "application/json, text/event-stream";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private Producto cafe;
    private String sesion;
    private int siguienteId = 1;

    @BeforeEach
    void iniciarSesionMcp() throws Exception {
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
        cafe = productoRepository.save(
                new Producto("CAF-500", "Café molido 500 g", "Despensa", new BigDecimal("19800.00"), 10, true));

        MvcResult inicio = mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", ACCEPT_MCP)
                        .content(JSON.writeValueAsString(Map.of(
                                "jsonrpc", "2.0", "id", 0, "method", "initialize",
                                "params", Map.of(
                                        "protocolVersion", "2025-06-18",
                                        "capabilities", Map.of(),
                                        "clientInfo", Map.of("name", "prueba", "version", "1"))))))
                .andReturn();
        assertThat(inicio.getResponse().getStatus()).isEqualTo(200);
        sesion = inicio.getResponse().getHeader("Mcp-Session-Id");
        assertThat(sesion).isNotBlank();
        JsonNode serverInfo = respuestaJsonRpc(inicio).path("result").path("serverInfo");
        assertThat(serverInfo.path("name").asText()).isEqualTo("supermercado-mcp-server");

        MvcResult notificacion = mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", ACCEPT_MCP)
                        .header("Mcp-Session-Id", sesion)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andReturn();
        assertThat(notificacion.getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("tools/list expone exactamente las diez herramientas con descripción y esquema")
    void exponeDiezHerramientas() throws Exception {
        JsonNode herramientas = solicitar("tools/list", Map.of()).path("result").path("tools");

        List<String> nombres = new ArrayList<>();
        herramientas.forEach(t -> {
            nombres.add(t.path("name").asText());
            assertThat(t.path("description").asText()).isNotBlank();
        });
        assertThat(nombres).containsExactlyInAnyOrder(
                "listarProductos", "buscarProductos", "consultarStock", "crearPedido", "agregarProducto",
                "retirarProducto", "consultarPedido", "confirmarPedido", "listarPedidos", "cancelarPedido");

        JsonNode agregar = null;
        for (JsonNode t : herramientas) {
            if (t.path("name").asText().equals("agregarProducto")) {
                agregar = t;
            }
        }
        assertThat(agregar).isNotNull();
        JsonNode esquema = agregar.path("inputSchema");
        assertThat(esquema.path("properties").has("pedidoId")).isTrue();
        assertThat(esquema.path("properties").has("productoId")).isTrue();
        assertThat(esquema.path("properties").has("cantidad")).isTrue();
        assertThat(esquema.path("required").toString()).contains("pedidoId", "productoId", "cantidad");
    }

    @Test
    @DisplayName("tools/call: crear, agregar y confirmar un pedido modifica el estado persistido")
    void flujoCompletoPorMcp() throws Exception {
        JsonNode creado = llamarHerramienta("crearPedido", Map.of(
                "clienteNombre", "Cliente MCP", "telefono", "3001234567", "direccion", "Carrera 1 # 2-3"));
        long pedidoId = creado.path("pedido").path("id").asLong();
        assertThat(creado.path("pedido").path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(creado.path("pedido").path("fechaCreacion").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

        JsonNode agregado = llamarHerramienta("agregarProducto", Map.of(
                "pedidoId", pedidoId, "productoId", cafe.getId(), "cantidad", 2));
        assertThat(agregado.path("pedido").path("total").decimalValue()).isEqualByComparingTo("39600.00");

        JsonNode confirmado = llamarHerramienta("confirmarPedido", Map.of("pedidoId", pedidoId));
        assertThat(confirmado.path("pedido").path("estado").asText()).isEqualTo("CONFIRMADO");
        assertThat(productoRepository.findById(cafe.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("los errores de negocio llegan al cliente MCP como resultado con isError y mensaje claro")
    void erroresLleganComoResultadoConError() throws Exception {
        JsonNode resultado = solicitar("tools/call", Map.of(
                "name", "agregarProducto",
                "arguments", Map.of("pedidoId", 1, "productoId", cafe.getId(), "cantidad", 0))).path("result");

        assertThat(resultado.path("isError").asBoolean()).isTrue();
        assertThat(resultado.path("content").get(0).path("text").asText())
                .contains("La cantidad debe ser un número entero mayor que cero");
    }

    // ---------- Auxiliares ----------

    /** Ejecuta tools/call, exige que no sea error y devuelve el JSON producido por la herramienta. */
    private JsonNode llamarHerramienta(String nombre, Map<String, Object> argumentos) throws Exception {
        JsonNode resultado = solicitar("tools/call", Map.of("name", nombre, "arguments", argumentos)).path("result");
        String texto = resultado.path("content").get(0).path("text").asText();
        assertThat(resultado.path("isError").asBoolean()).as(texto).isFalse();
        return JSON.readTree(texto);
    }

    private JsonNode solicitar(String metodo, Map<String, Object> parametros) throws Exception {
        MvcResult resultado = mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", ACCEPT_MCP)
                        .header("Mcp-Session-Id", sesion)
                        .content(JSON.writeValueAsString(Map.of(
                                "jsonrpc", "2.0", "id", siguienteId++, "method", metodo, "params", parametros))))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return respuestaJsonRpc(resultado);
    }

    /** La respuesta llega como JSON simple o como evento SSE ("data:{...}") según la operación. */
    private static JsonNode respuestaJsonRpc(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String linea : cuerpo.split("\\R")) {
            if (linea.startsWith("data:")) {
                return JSON.readTree(linea.substring("data:".length()));
            }
        }
        return JSON.readTree(cuerpo);
    }
}

package com.udea.supermercadomcp.service;

import com.udea.supermercadomcp.exception.DatoInvalidoException;
import com.udea.supermercadomcp.exception.ReglaNegocioException;
import com.udea.supermercadomcp.model.EstadoPedido;
import com.udea.supermercadomcp.model.Pedido;
import com.udea.supermercadomcp.model.Producto;

import java.util.regex.Pattern;

/**
 * Punto único de validación de argumentos y reglas de negocio.
 * Cada método lanza una excepción con un mensaje determinista; nunca devuelve null.
 */
final class ValidacionesSupermercado {

    static final int MAX_CLIENTE_NOMBRE = 120;
    static final int MAX_TELEFONO = 30;
    static final int MAX_DIRECCION = 200;
    static final int MAX_TEXTO_BUSQUEDA = 120;

    private static final Pattern TELEFONO = Pattern.compile("[0-9+()\\-\\s]+");
    private static final int MIN_DIGITOS_TELEFONO = 7;

    private ValidacionesSupermercado() {
    }

    // ---------- Argumentos ----------

    static Long exigirId(Long id, String parametro) {
        if (id == null || id <= 0) {
            throw new DatoInvalidoException(String.format(
                    "El parámetro '%s' es obligatorio y debe ser un número entero positivo; se recibió: %s.",
                    parametro, id));
        }
        return id;
    }

    /** Devuelve el texto sin espacios sobrantes. */
    static String exigirTexto(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new DatoInvalidoException(String.format(
                    "El campo '%s' es obligatorio y no puede estar vacío.", campo));
        }
        String limpio = valor.strip();
        if (limpio.length() > longitudMaxima) {
            throw new DatoInvalidoException(String.format(
                    "El campo '%s' no puede superar %d caracteres.", campo, longitudMaxima));
        }
        return limpio;
    }

    static String exigirTelefono(String valor) {
        String telefono = exigirTexto(valor, "telefono", MAX_TELEFONO);
        long digitos = telefono.chars().filter(Character::isDigit).count();
        if (!TELEFONO.matcher(telefono).matches() || digitos < MIN_DIGITOS_TELEFONO) {
            throw new DatoInvalidoException(String.format(
                    "El campo 'telefono' no es válido: use solo dígitos, espacios, '+', '-' o paréntesis, con al menos %d dígitos.",
                    MIN_DIGITOS_TELEFONO));
        }
        return telefono;
    }

    static int exigirCantidadPositiva(Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new DatoInvalidoException(String.format(
                    "La cantidad debe ser un número entero mayor que cero; se recibió: %s.", cantidad));
        }
        return cantidad;
    }

    // ---------- Reglas del pedido ----------

    static void exigirBorrador(Pedido pedido, String accion) {
        if (pedido.getEstado() != EstadoPedido.BORRADOR) {
            throw new ReglaNegocioException(String.format(
                    "El pedido %d está en estado %s; solo se puede %s un pedido en estado BORRADOR.",
                    pedido.getId(), pedido.getEstado(), accion));
        }
    }

    static void exigirConfirmable(Pedido pedido) {
        if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
            throw new ReglaNegocioException(String.format(
                    "El pedido %d ya fue confirmado; no se puede confirmar nuevamente.", pedido.getId()));
        }
        exigirBorrador(pedido, "confirmar");
        if (pedido.getDetalles().isEmpty()) {
            throw new ReglaNegocioException(String.format(
                    "El pedido %d no tiene productos; agregue al menos uno antes de confirmarlo.", pedido.getId()));
        }
    }

    static void exigirCancelable(Pedido pedido) {
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ReglaNegocioException(String.format(
                    "El pedido %d ya está cancelado; no se puede cancelar nuevamente.", pedido.getId()));
        }
    }

    // ---------- Reglas del producto ----------

    static void exigirDisponible(Producto producto) {
        if (!Boolean.TRUE.equals(producto.getDisponible())) {
            throw new ReglaNegocioException(String.format(
                    "El producto '%s' (SKU %s) no está disponible para la venta.",
                    producto.getNombre(), producto.getSku()));
        }
    }

    /** {@code cantidadRequerida} es long para que la suma con lo ya pedido no desborde. */
    static void exigirStockSuficiente(Producto producto, long cantidadRequerida) {
        if (cantidadRequerida > producto.getStock()) {
            throw new ReglaNegocioException(String.format(
                    "Stock insuficiente para '%s' (SKU %s): se requieren %d unidades y hay %d disponibles.",
                    producto.getNombre(), producto.getSku(), cantidadRequerida, producto.getStock()));
        }
    }
}

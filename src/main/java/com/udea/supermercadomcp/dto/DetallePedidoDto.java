package com.udea.supermercadomcp.dto;

import com.udea.supermercadomcp.model.DetallePedido;
import com.udea.supermercadomcp.model.Producto;

import java.math.BigDecimal;

/** Línea de un pedido; referencia al producto por id/SKU/nombre para evitar ciclos JSON. */
public record DetallePedidoDto(
        Long productoId,
        String sku,
        String nombreProducto,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal) {

    public static DetallePedidoDto desde(DetallePedido detalle) {
        Producto producto = detalle.getProducto();
        return new DetallePedidoDto(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal());
    }
}

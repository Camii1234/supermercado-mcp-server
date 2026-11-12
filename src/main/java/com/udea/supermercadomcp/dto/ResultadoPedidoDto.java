package com.udea.supermercadomcp.dto;

/** Resultado de una operación de escritura: mensaje legible y estado actualizado del pedido. */
public record ResultadoPedidoDto(
        String mensaje,
        PedidoDto pedido) {
}

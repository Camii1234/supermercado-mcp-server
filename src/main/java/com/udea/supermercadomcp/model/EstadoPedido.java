package com.udea.supermercadomcp.model;

/**
 * Ciclo de vida de un pedido: BORRADOR -> CONFIRMADO -> CANCELADO,
 * o directamente BORRADOR -> CANCELADO.
 */
public enum EstadoPedido {
    BORRADOR,
    CONFIRMADO,
    CANCELADO
}

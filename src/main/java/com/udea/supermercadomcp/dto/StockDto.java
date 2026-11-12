package com.udea.supermercadomcp.dto;

/** Existencias de un producto y si puede venderse en este momento. */
public record StockDto(
        ProductoDto producto,
        boolean aLaVenta,
        String mensaje) {
}

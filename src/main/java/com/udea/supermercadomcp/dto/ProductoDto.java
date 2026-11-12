package com.udea.supermercadomcp.dto;

import com.udea.supermercadomcp.model.Producto;

import java.math.BigDecimal;

/** Producto tal como se expone al chatbot (sin campos internos como la versión). */
public record ProductoDto(
        Long id,
        String sku,
        String nombre,
        String categoria,
        BigDecimal precio,
        Integer stock,
        Boolean disponible) {

    public static ProductoDto desde(Producto producto) {
        return new ProductoDto(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getCategoria(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getDisponible());
    }
}

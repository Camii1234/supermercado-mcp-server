package com.udea.supermercadomcp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.udea.supermercadomcp.model.EstadoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila del listado de pedidos; se construye directamente desde la consulta JPQL. */
public record PedidoResumenDto(
        Long id,
        String clienteNombre,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime fechaCreacion,
        EstadoPedido estado,
        BigDecimal total,
        Long cantidadProductos) {
}

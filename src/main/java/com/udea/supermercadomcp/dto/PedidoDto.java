package com.udea.supermercadomcp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.udea.supermercadomcp.model.EstadoPedido;
import com.udea.supermercadomcp.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Resumen completo de un pedido con sus detalles. */
public record PedidoDto(
        Long id,
        String clienteNombre,
        String telefono,
        String direccion,
        // texto ISO-8601: el mapper de Spring AI escribiría la fecha como arreglo numérico
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime fechaCreacion,
        EstadoPedido estado,
        List<DetallePedidoDto> detalles,
        BigDecimal total) {

    /** El pedido debe tener cargados sus detalles y productos (ver PedidoRepository.findConDetallesById). */
    public static PedidoDto desde(Pedido pedido) {
        return new PedidoDto(
                pedido.getId(),
                pedido.getClienteNombre(),
                pedido.getTelefono(),
                pedido.getDireccion(),
                pedido.getFechaCreacion(),
                pedido.getEstado(),
                pedido.getDetalles().stream().map(DetallePedidoDto::desde).toList(),
                pedido.getTotal());
    }
}

package com.udea.supermercadomcp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Línea de un pedido. Un pedido no puede tener dos líneas del mismo producto
 * (restricción única pedido_id + producto_id); en su lugar se incrementa la cantidad.
 * La relación con Producto es unidireccional: el producto no conoce sus detalles.
 */
@Entity
@Table(name = "detalle_pedido",
        uniqueConstraints = @UniqueConstraint(name = "uk_detalle_pedido_producto",
                columnNames = {"pedido_id", "producto_id"}),
        indexes = @Index(name = "idx_detalle_pedido_producto", columnList = "producto_id"))
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_detalle_pedido_pedido"))
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_detalle_pedido_producto"))
    private Producto producto;

    @NotNull
    @Positive
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @NotNull
    @Positive
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @NotNull
    @Positive
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected DetallePedido() {
        // requerido por JPA
    }

    /** Conserva el precio vigente del producto en el momento de agregarlo. */
    public DetallePedido(Producto producto, int cantidad) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = producto.getPrecio();
        recalcularSubtotal();
    }

    void asignarPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public void incrementarCantidad(int adicional) {
        cantidad += adicional;
        recalcularSubtotal();
    }

    public void recalcularSubtotal() {
        subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Producto getProducto() {
        return producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetallePedido otro)) return false;
        return id != null && id.equals(otro.getId());
    }

    @Override
    public int hashCode() {
        // constante: el id se asigna al persistir y no debe alterar el hash
        return DetallePedido.class.hashCode();
    }

    @Override
    public String toString() {
        return "DetallePedido{id=" + id + ", cantidad=" + cantidad + ", subtotal=" + subtotal + '}';
    }
}

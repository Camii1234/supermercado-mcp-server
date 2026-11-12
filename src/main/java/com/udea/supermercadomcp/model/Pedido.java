package com.udea.supermercadomcp.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pedido a domicilio. Es la raíz del agregado: los detalles solo se crean,
 * modifican o eliminan a través del pedido (cascade ALL + orphanRemoval).
 */
@Entity
@Table(name = "pedido",
        indexes = {
                @Index(name = "idx_pedido_estado", columnList = "estado"),
                @Index(name = "idx_pedido_fecha_creacion", columnList = "fecha_creacion")
        })
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(name = "cliente_nombre", nullable = false, length = 120)
    private String clienteNombre;

    @NotBlank
    @Size(max = 30)
    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;

    @NotBlank
    @Size(max = 200)
    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado;

    @NotNull
    @PositiveOrZero
    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;  // bloqueo optimista

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetallePedido> detalles = new ArrayList<>();

    protected Pedido() {
        // requerido por JPA
    }

    public Pedido(String clienteNombre, String telefono, String direccion, LocalDateTime fechaCreacion) {
        this.clienteNombre = clienteNombre;
        this.telefono = telefono;
        this.direccion = direccion;
        this.fechaCreacion = fechaCreacion;
        this.estado = EstadoPedido.BORRADOR;
        this.total = BigDecimal.ZERO.setScale(2);
    }

    public Optional<DetallePedido> buscarDetalle(Long productoId) {
        return detalles.stream()
                .filter(d -> d.getProducto().getId().equals(productoId))
                .findFirst();
    }

    public void agregarDetalle(DetallePedido detalle) {
        detalles.add(detalle);
        detalle.asignarPedido(this);
    }

    /** Al quitarlo de la colección, orphanRemoval elimina la fila del detalle. */
    public void retirarDetalle(DetallePedido detalle) {
        detalles.remove(detalle);
    }

    /** Recalcula cada subtotal y el total a partir de cantidad x precio unitario. */
    public void recalcularTotal() {
        BigDecimal suma = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            detalle.recalcularSubtotal();
            suma = suma.add(detalle.getSubtotal());
        }
        total = suma;
    }

    public void marcarConfirmado() {
        estado = EstadoPedido.CONFIRMADO;
    }

    public void marcarCancelado() {
        estado = EstadoPedido.CANCELADO;
    }

    public Long getId() {
        return id;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Long getVersion() {
        return version;
    }

    /** Vista de solo lectura; las modificaciones pasan por agregarDetalle/retirarDetalle. */
    public List<DetallePedido> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido otro)) return false;
        return id != null && id.equals(otro.getId());
    }

    @Override
    public int hashCode() {
        // constante: el id se asigna al persistir y no debe alterar el hash
        return Pedido.class.hashCode();
    }

    @Override
    public String toString() {
        return "Pedido{id=" + id + ", estado=" + estado + ", total=" + total + '}';
    }
}

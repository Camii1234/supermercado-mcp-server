package com.udea.supermercadomcp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Producto del catálogo del supermercado.
 * El SKU es la clave natural: único e inmutable (no tiene setter y la columna no es actualizable).
 */
@Entity
@Table(name = "producto",
        uniqueConstraints = @UniqueConstraint(name = "uk_producto_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_producto_nombre", columnList = "nombre"),
                @Index(name = "idx_producto_categoria", columnList = "categoria"),
                @Index(name = "idx_producto_disponible", columnList = "disponible")
        })
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "sku", nullable = false, length = 40, updatable = false)
    private String sku;

    @NotBlank
    @Size(max = 120)
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @NotBlank
    @Size(max = 60)
    @Column(name = "categoria", nullable = false, length = 60)
    private String categoria;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @NotNull
    @PositiveOrZero
    @Column(name = "stock", nullable = false)
    private Integer stock;

    @NotNull
    @Column(name = "disponible", nullable = false)
    private Boolean disponible = Boolean.TRUE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;  // bloqueo optimista

    protected Producto() {
        // requerido por JPA
    }

    public Producto(String sku, String nombre, String categoria, BigDecimal precio, Integer stock, Boolean disponible) {
        this.sku = sku;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.stock = stock;
        this.disponible = disponible;
    }

    /** Disponible para la venta: marcado como disponible y con existencias. */
    public boolean estaALaVenta() {
        return Boolean.TRUE.equals(disponible) && stock != null && stock > 0;
    }

    public void descontarStock(int cantidad) {
        if (cantidad <= 0 || cantidad > stock) {
            throw new IllegalStateException("Descuento de stock inválido para el producto " + sku);
        }
        stock -= cantidad;
    }

    public void restituirStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalStateException("Restitución de stock inválida para el producto " + sku);
        }
        stock += cantidad;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Producto otro)) return false;
        // getter y no campo: el otro objeto puede ser un proxy perezoso de Hibernate
        return sku != null && sku.equals(otro.getSku());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sku);
    }

    @Override
    public String toString() {
        return "Producto{id=" + id + ", sku='" + sku + "', nombre='" + nombre + "', stock=" + stock + '}';
    }
}

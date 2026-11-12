package com.udea.supermercadomcp.repository;

import com.udea.supermercadomcp.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Productos a la venta: marcados como disponibles y con existencias. */
    @Query("""
            select p from Producto p
            where p.disponible = true and p.stock > 0
            order by p.categoria asc, p.nombre asc
            """)
    List<Producto> findDisponibles();

    List<Producto> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);

    List<Producto> findByCategoriaIgnoreCaseOrderByNombreAsc(String categoria);

    Optional<Producto> findBySkuIgnoreCase(String sku);

    /** Búsqueda parcial, insensible a mayúsculas, por nombre, categoría o SKU. */
    @Query("""
            select p from Producto p
            where lower(p.nombre) like lower(concat('%', :texto, '%'))
               or lower(p.categoria) like lower(concat('%', :texto, '%'))
               or lower(p.sku) like lower(concat('%', :texto, '%'))
            order by p.categoria asc, p.nombre asc
            """)
    List<Producto> buscarPorTexto(@Param("texto") String texto);
}

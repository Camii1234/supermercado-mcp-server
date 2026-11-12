package com.udea.supermercadomcp.repository;

import com.udea.supermercadomcp.dto.PedidoResumenDto;
import com.udea.supermercadomcp.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Carga el pedido junto con sus detalles y el producto de cada detalle en una sola consulta,
     * evitando N+1 y LazyInitializationException al construir las respuestas MCP.
     */
    @EntityGraph(attributePaths = {"detalles", "detalles.producto"})
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findConDetallesById(@Param("id") Long id);

    /** Resumen de todos los pedidos en una única consulta agregada, ordenado por id. */
    @Query("""
            select new com.udea.supermercadomcp.dto.PedidoResumenDto(
                p.id, p.clienteNombre, p.fechaCreacion, p.estado, p.total, count(d))
            from Pedido p left join p.detalles d
            group by p.id, p.clienteNombre, p.fechaCreacion, p.estado, p.total
            order by p.id asc
            """)
    List<PedidoResumenDto> listarResumenes();
}

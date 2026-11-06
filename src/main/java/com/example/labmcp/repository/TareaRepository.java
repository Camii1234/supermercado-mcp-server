package com.example.labmcp.repository;

import com.example.labmcp.model.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByCompletado(Boolean completado);
    List<Tarea> findByTituloContainingIgnoreCase(String fragmento);


}

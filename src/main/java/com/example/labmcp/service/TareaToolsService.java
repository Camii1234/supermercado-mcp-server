package com.example.labmcp.service;

import com.example.labmcp.model.Tarea;
import com.example.labmcp.repository.TareaRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de tareas expuesto como herramientas MCP.
 *
 * Cada método @Tool se publica automáticamente por el servidor MCP:
 *   - nombre      = nombre del método
 *   - descripción = atributo description
 *   - JSON schema      = derivado de los parámetros (incluye @ToolParam)
 *
 * Claude (desde Claude Desktop) podrá invocar estos métodos cuando el usuario
 * hable en lenguaje natural: "créame una tarea para el lunes", "márcalas todas
 * como completadas", etc.
 */
@Component
public class TareaToolsService {

    private final TareaRepository repo;

    public TareaToolsService(TareaRepository repo) {
        this.repo = repo;
    }

    // ---------- READ ----------

    @Tool(name = "listTareas", description = "Devuelve la lista completa de tareas almacenadas.")
    public List<Tarea> listarTareas() {
        return repo.findAll();
    }

    @Tool(name = "listPendientes", description = "Devuelve únicamente las tareas pendientes (completado = false).")
    public List<Tarea> listarPendientes() {
        return repo.findByCompletado(false);
    }

    @Tool(name = "buscarTareas", description = "Busca tareas cuyo título contiene el fragmento indicado (case-insensitive).")
    public List<Tarea> buscarTareas(
            @ToolParam(description = "Fragmento de texto a buscar en el título") String fragmento) {
        return repo.findByTituloContainingIgnoreCase(fragmento);
    }

    // ---------- CREATE ----------

    @Tool(name = "crearTarea", description = "Crea una nueva tarea con el título y fecha indicados. Por defecto queda como pendiente.")
    @Transactional
    public Tarea crearTarea(
            @ToolParam(description = "Título descriptivo de la tarea") String titulo,
            @ToolParam(description = "Fecha asociada, formato libre (p.ej. 2026-04-20)") String fecha) {
        Tarea t = new Tarea(titulo, fecha, Boolean.FALSE);
        return repo.save(t);
    }

    // ---------- UPDATE ----------

    @Tool(name = "markComplete", description = "Marca como completada la tarea con el id indicado.")
    @Transactional
    public Tarea marcarCompletada(
            @ToolParam(description = "Identificador único de la tarea") Long id) {
        Tarea t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        t.setCompletado(Boolean.TRUE);
        return repo.save(t);
    }

    @Tool(name = "updateTarea", description = "Actualiza el título o la fecha de una tarea existente. Los parámetros nulos no se modifican.")
    @Transactional
    public Tarea actualizarTarea(
            @ToolParam(description = "Id de la tarea a actualizar") Long id,
            @ToolParam(description = "Nuevo título (opcional, null = no cambiar)") String titulo,
            @ToolParam(description = "Nueva fecha (opcional, null = no cambiar)") String fecha) {
        Tarea t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        if (titulo != null && !titulo.isBlank()) t.setTitulo(titulo);
        if (fecha  != null && !fecha.isBlank())  t.setFecha(fecha);
        return repo.save(t);
    }

    // ---------- DELETE ----------

    @Tool(name = "eliminarTarea", description = "Elimina la tarea con el id indicado. Devuelve un mensaje de confirmación.")
    @Transactional
    public String eliminarTarea(
            @ToolParam(description = "Id de la tarea a eliminar") Long id) {
        if (!repo.existsById(id)) {
            return "La tarea " + id + " no existe.";
        }
        repo.deleteById(id);
        return "Tarea " + id + " eliminada correctamente.";
    }
}
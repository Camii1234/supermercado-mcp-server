package com.example.labmcp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import javax.annotation.processing.SupportedSourceVersion;

@Entity
@Table(name="tarea")
public class Tarea {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String titulo;

    private String fecha;

    private Boolean completado;

    @Version
    private Long version;  //bloqueo optimista

    public Tarea(){}

    public Tarea(String titulo, String Fecha, Boolean completado ){
        this.titulo=titulo;
        this.fecha=fecha;
        this.completado=completado;

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public Boolean getCompletado() {
        return completado;
    }

    public void setCompletado(Boolean completado) {
        this.completado = completado;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

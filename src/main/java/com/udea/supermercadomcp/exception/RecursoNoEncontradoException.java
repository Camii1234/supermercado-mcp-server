package com.udea.supermercadomcp.exception;

/** El producto o pedido solicitado no existe. */
public class RecursoNoEncontradoException extends SupermercadoException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

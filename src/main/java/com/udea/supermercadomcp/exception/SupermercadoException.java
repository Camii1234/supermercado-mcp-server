package com.udea.supermercadomcp.exception;

/**
 * Base de los errores de negocio. El servidor MCP devuelve su mensaje al cliente
 * como resultado de herramienta con error, por eso los mensajes deben ser claros y deterministas.
 */
public abstract class SupermercadoException extends RuntimeException {

    protected SupermercadoException(String mensaje) {
        super(mensaje);
    }

    protected SupermercadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

package com.udea.supermercadomcp.exception;

/** La operación viola una regla del negocio (estado del pedido, stock, disponibilidad...). */
public class ReglaNegocioException extends SupermercadoException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }

    public ReglaNegocioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

package com.udea.supermercadomcp.exception;

/** Argumento ausente o con formato inválido (id, texto vacío, cantidad no positiva...). */
public class DatoInvalidoException extends SupermercadoException {

    public DatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

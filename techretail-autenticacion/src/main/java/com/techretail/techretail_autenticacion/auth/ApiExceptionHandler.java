package com.techretail.techretail_autenticacion.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones del modulo al sobre de error unico
 * {@code {"status", "error", "message"}}.
 *
 * <p>Cada respuesta repite las cabeceras de seguridad que el resto del sistema
 * ya emite: {@code no-store} porque un error de credenciales no se cachea,
 * {@code nosniff} y {@code X-Frame-Options: DENY} porque el cliente puede
 * renderizar el cuerpo.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<ApiError> credencialesInvalidas() {
        return respuesta(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    @ExceptionHandler(UsuarioDuplicadoException.class)
    ResponseEntity<ApiError> usuarioDuplicado() {
        return respuesta(HttpStatus.CONFLICT, "El usuario ya esta registrado");
    }

    /** Dos altas simultaneas del mismo usuario: la restriccion unica decide. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> restriccionDeIntegridad() {
        return respuesta(HttpStatus.CONFLICT, "El usuario ya esta registrado");
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<ApiError> solicitudInvalida(SolicitudInvalidaException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud invalida: " + excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validacionFallida(MethodArgumentNotValidException excepcion) {
        FieldError error = excepcion.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String detalle = error == null
                ? "revise los datos enviados"
                : error.getField() + " " + error.getDefaultMessage();
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud invalida: " + detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> cuerpoIlegible() {
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud invalida: el cuerpo JSON no se puede leer");
    }

    private static ResponseEntity<ApiError> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .body(new ApiError(status.value(), status.getReasonPhrase(), mensaje));
    }
}

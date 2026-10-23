package com.techretail.techretail_producto.producto;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Contrato de error comun: {@code {status, error, message, timestamp}}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(int status, String error, String message, LocalDateTime timestamp) {
    }

    @ExceptionHandler({ProductoNoEncontradoException.class, CategoriaNoEncontradaException.class})
    ResponseEntity<ApiError> noEncontrado(RuntimeException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted().collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, mensaje);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> peticionMalFormada(Exception ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Peticion mal formada");
    }

    private static ResponseEntity<ApiError> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado)
                .body(new ApiError(estado.value(), estado.getReasonPhrase(), mensaje, LocalDateTime.now()));
    }
}

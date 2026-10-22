package com.techretail.techretail_gateway.audit;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de auditoria fuera del hilo de la peticion.
 *
 * <p>El encolado nunca lanza ni espera: si el ejecutor esta saturado el evento
 * se descarta con un aviso en el log, y un fallo de escritura en
 * {@code db_esb} tampoco llega al cliente.</p>
 */
@Component
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final AuditLogRepository repositorio;
    private final Executor ejecutor;

    public AuditPublisher(AuditLogRepository repositorio, @Qualifier("auditExecutor") Executor ejecutor) {
        this.repositorio = repositorio;
        this.ejecutor = ejecutor;
    }

    public void publicar(AuditEntry entrada) {
        try {
            ejecutor.execute(() -> escribir(entrada));
        } catch (RejectedExecutionException ex) {
            log.warn("Cola de auditoria saturada; se descarta {} {}", entrada.operacion(), entrada.endpoint());
        }
    }

    private void escribir(AuditEntry entrada) {
        try {
            repositorio.registrar(entrada);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la auditoria de {} {}: {}",
                    entrada.operacion(), entrada.endpoint(), ex.getMessage());
        }
    }
}

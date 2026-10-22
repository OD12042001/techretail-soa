package com.techretail.techretail_gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techretail.techretail_gateway.audit.AuditEntry;
import com.techretail.techretail_gateway.audit.AuditLogRepository;
import com.techretail.techretail_gateway.audit.AuditPublisher;
import com.techretail.techretail_gateway.audit.ServiceDestinationResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fase 2.2: la auditoria se escribe fuera del hilo de la peticion, de modo que
 * una base de datos lenta o caida nunca bloquea ni rompe al cliente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaAsincronaTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogRepository repositorio;

    @Test
    void unaEscrituraLentaDeAuditoriaNoBloqueaLaPeticion() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(invocacion -> {
            liberar.await(5, TimeUnit.SECONDS);
            return null;
        }).when(repositorio).registrar(any(AuditEntry.class));

        long inicio = System.nanoTime();
        mockMvc.perform(get("/api/clientes/lento")).andExpect(status().isUnauthorized());
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;

        assertThat(duracionMs).as("la peticion no espera a la escritura de auditoria").isLessThan(2_000L);

        liberar.countDown();
        verify(repositorio, timeout(3_000)).registrar(any(AuditEntry.class));
    }

    @Test
    void unFalloDelRepositorioNoAfectaLaRespuesta() throws Exception {
        doAnswer(invocacion -> {
            throw new IllegalStateException("db_esb caida");
        }).when(repositorio).registrar(any(AuditEntry.class));

        mockMvc.perform(get("/api/clientes/db-caida")).andExpect(status().isUnauthorized());

        verify(repositorio, timeout(3_000)).registrar(any(AuditEntry.class));
    }

    @Test
    void elPublicadorEjecutaEnOtroHilo() throws Exception {
        List<String> hilos = new ArrayList<>();
        CountDownLatch hecho = new CountDownLatch(1);
        AuditLogRepository destino = new AuditLogRepository(new JdbcTemplate()) {
            @Override
            public void registrar(AuditEntry entrada) {
                hilos.add(Thread.currentThread().getName());
                hecho.countDown();
            }
        };
        Executor ejecutor = comando -> new Thread(comando, "hilo-auditoria-prueba").start();

        new AuditPublisher(destino, ejecutor).publicar(new AuditEntry(
                "GATEWAY", new ServiceDestinationResolver().resolver("/api/clientes/x"),
                "GET", "/api/clientes/x", 200, "anonimo", "d"));

        assertThat(hecho.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(hilos).containsExactly("hilo-auditoria-prueba");
    }

    @Test
    void unaColaSaturadaDescartaSinLanzarExcepcion() {
        Executor saturado = comando -> {
            throw new java.util.concurrent.RejectedExecutionException("cola llena");
        };
        AuditPublisher publicador = new AuditPublisher(repositorio, saturado);

        publicador.publicar(new AuditEntry("GATEWAY", "x", "GET", "/api/x", 200, "u", "d"));
        // Si no lanza, el cliente no se entera: el descarte queda solo en el log.
    }
}

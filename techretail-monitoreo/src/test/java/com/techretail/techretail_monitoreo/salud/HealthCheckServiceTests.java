package com.techretail.techretail_monitoreo.salud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class HealthCheckServiceTests {

    @Autowired
    private HealthCheckService servicio;

    @Autowired
    private MonitoreoProperties propiedades;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private HealthProbe sonda;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM monitoreo_logs");
    }

    @Test
    void hayUnServicioObjetivoPorCadaMicroservicio() {
        assertThat(propiedades.servicios()).extracting(ServicioObjetivo::nombre)
                .contains("techretail-cliente", "techretail-pedido", "techretail-gateway")
                .doesNotContain("techretail-monitoreo");
    }

    @Test
    void unCicloRegistraUnaFilaPorServicioConEstadoYTiempo() {
        when(sonda.sondear(any())).thenReturn(new ResultadoSonda("UP", 12));

        servicio.ejecutarCiclo();

        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM monitoreo_logs", Integer.class);
        assertThat(filas).isEqualTo(propiedades.servicios().size());
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT nombre_servicio, estado, tiempo_respuesta_ms FROM monitoreo_logs LIMIT 1");
        assertThat(fila.get("estado")).isEqualTo("UP");
        assertThat(fila.get("tiempo_respuesta_ms")).isEqualTo(12);
    }

    @Test
    void unaSondaQueLanzaExcepcionSeRegistraComoDownYElCicloContinua() {
        ServicioObjetivo primero = propiedades.servicios().get(0);
        when(sonda.sondear(any())).thenAnswer(inv -> {
            ServicioObjetivo s = inv.getArgument(0);
            if (s.nombre().equals(primero.nombre())) {
                throw new IllegalStateException("explota");
            }
            return new ResultadoSonda("UP", 5);
        });

        servicio.ejecutarCiclo();

        List<String> estados = jdbc.queryForList(
                "SELECT estado FROM monitoreo_logs WHERE nombre_servicio = ?", String.class, primero.nombre());
        assertThat(estados).containsExactly("DOWN");
        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM monitoreo_logs", Integer.class);
        assertThat(filas).isEqualTo(propiedades.servicios().size());
    }
}

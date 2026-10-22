package com.techretail.techretail_monitoreo.salud;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "techretail.monitoreo.scheduler.enabled=true",
        "techretail.monitoreo.intervalo-ms=200"})
@ActiveProfiles("test")
class HealthCheckSchedulerTests {

    @MockitoBean
    private HealthProbe sonda;

    @Autowired
    private MonitoreoProperties propiedades;

    @Test
    void elPlanificadorEjecutaCiclosPeriodicos() {
        when(sonda.sondear(any())).thenReturn(new ResultadoSonda("UP", 1));

        // Al menos dos ciclos completos: 2 sondeos por cada servicio.
        verify(sonda, timeout(5_000).atLeast(propiedades.servicios().size() * 2)).sondear(any());
    }
}

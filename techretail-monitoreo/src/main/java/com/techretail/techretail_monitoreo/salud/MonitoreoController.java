package com.techretail.techretail_monitoreo.salud;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consulta del estado de los servicios. El acceso (ADMIN y EMPLEADO) lo impone
 * el gateway sobre {@code /api/monitoreo/**}; este servicio no se expone
 * directamente fuera del BUS.
 */
@RestController
public class MonitoreoController {

    static final int LIMITE_MAXIMO = 500;

    private final MonitoreoLogRepository repositorio;

    public MonitoreoController(MonitoreoLogRepository repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping("/api/monitoreo/estado")
    public List<MonitoreoLogView> estado() {
        return repositorio.ultimaPorServicio();
    }

    @GetMapping("/api/monitoreo/historial")
    public List<MonitoreoLogView> historial(@RequestParam(name = "limite", defaultValue = "50") int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limite debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        return repositorio.recientes(limite);
    }
}

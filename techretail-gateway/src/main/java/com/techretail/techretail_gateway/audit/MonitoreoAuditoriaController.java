package com.techretail.techretail_gateway.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monitoreo en tiempo real de la auditoria del BUS. El acceso (ADMIN y
 * EMPLEADO) lo impone {@code SecurityConfig} sobre {@code /api/auditoria/**} y {@code /api/monitoreo/**};
 * estas rutas son locales (no se enrutan a otro servicio).
 */
@RestController
public class MonitoreoAuditoriaController {

    static final int LIMITE_MAXIMO = 500;

    private final AuditLogRepository repositorio;

    public MonitoreoAuditoriaController(AuditLogRepository repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping("/api/auditoria")
    public List<AuditLogView> recientes(@RequestParam(name = "limite", defaultValue = "50") int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limite debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        return repositorio.recientes(limite);
    }

    @GetMapping("/api/auditoria/resumen")
    public Map<String, Object> resumen() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("total", repositorio.contar());
        cuerpo.put("errores", repositorio.contarErrores());
        cuerpo.put("porServicio", repositorio.contarPorServicio());
        return cuerpo;
    }
}

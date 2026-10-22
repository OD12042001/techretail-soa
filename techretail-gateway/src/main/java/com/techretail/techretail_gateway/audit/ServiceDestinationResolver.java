package com.techretail.techretail_gateway.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Traduce la ruta de entrada a la columna {@code servicio_destino} de
 * {@code auditoria_logs}, segun la tabla de direccionamiento del Avance2.
 */
@Component
public class ServiceDestinationResolver {

    private static final Map<String, String> DESTINOS = crearDestinos();
    private static final String DESCONOCIDO = "desconocido";

    private static Map<String, String> crearDestinos() {
        Map<String, String> destinos = new LinkedHashMap<>();
        destinos.put("/api/clientes", "techretail-cliente");
        destinos.put("/api/productos", "techretail-producto");
        destinos.put("/api/pedidos", "techretail-pedido");
        destinos.put("/api/pagos", "techretail-pago");
        destinos.put("/api/inventario", "techretail-inventario");
        destinos.put("/api/notificaciones", "techretail-notificacion");
        destinos.put("/ws/facturacion", "techretail-facturacion-sunat");
        destinos.put("/api/auth", "techretail-autenticacion");
        destinos.put("/api/monitoreo", "techretail-monitoreo");
        destinos.put("/api/registro", "techretail-gateway");
        return Collections.unmodifiableMap(destinos);
    }

    public String resolver(String ruta) {
        for (Map.Entry<String, String> destino : DESTINOS.entrySet()) {
            if (ruta.startsWith(destino.getKey())) {
                return destino.getValue();
            }
        }
        return DESCONOCIDO;
    }
}

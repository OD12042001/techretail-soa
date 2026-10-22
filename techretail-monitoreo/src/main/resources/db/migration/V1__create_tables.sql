CREATE TABLE monitoreo_logs (
    id BIGSERIAL PRIMARY KEY,
    nombre_servicio VARCHAR(50) NOT NULL,
    estado VARCHAR(10) NOT NULL,
    tiempo_respuesta_ms INT NOT NULL,
    fecha_consulta TIMESTAMP NOT NULL DEFAULT NOW()
);

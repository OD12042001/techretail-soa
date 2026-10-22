CREATE TABLE notificaciones (
    id BIGSERIAL PRIMARY KEY,
    destinatario VARCHAR(120) NOT NULL,
    tipo_evento VARCHAR(50) NOT NULL,
    canal VARCHAR(20) NOT NULL,
    mensaje TEXT NOT NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'ENVIADO',
    fecha_envio TIMESTAMP NOT NULL DEFAULT NOW()
);

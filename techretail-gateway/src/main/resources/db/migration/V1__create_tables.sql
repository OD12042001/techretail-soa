CREATE TABLE auditoria_logs (
    id BIGSERIAL PRIMARY KEY,
    servicio_origen VARCHAR(50) NOT NULL,
    servicio_destino VARCHAR(50) NOT NULL,
    operacion VARCHAR(10) NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    estado_respuesta INT NOT NULL,
    usuario VARCHAR(100),
    timestamp TIMESTAMP DEFAULT NOW(),
    detalle TEXT
);

CREATE TABLE servicios_registro (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE,
    url VARCHAR(120) NOT NULL,
    protocolo VARCHAR(10) NOT NULL,       -- REST / SOAP
    formato VARCHAR(10) NOT NULL,       -- JSON / XML
    wsdl VARCHAR(160),
    version VARCHAR(10) NOT NULL DEFAULT '1.0',
    estado VARCHAR(10) NOT NULL DEFAULT 'UP',
    fecha_registro TIMESTAMP DEFAULT NOW()
);

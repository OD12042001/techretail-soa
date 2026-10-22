CREATE TABLE facturas (
    id BIGSERIAL PRIMARY KEY,
    ruc VARCHAR(20) NOT NULL,
    monto NUMERIC(10,2) NOT NULL,
    descripcion TEXT NOT NULL,
    numero_factura VARCHAR(50),
    estado VARCHAR(15) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_registro TIMESTAMP DEFAULT NOW()
);

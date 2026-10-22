CREATE TABLE pagos (
    id BIGSERIAL PRIMARY KEY,
    id_pedido BIGINT NOT NULL,
    monto NUMERIC(10,2) NOT NULL,
    metodo_pago VARCHAR(15) NOT NULL,
    numero_tarjeta VARCHAR(20),
    titular VARCHAR(100),
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    operacion VARCHAR(50),
    fecha TIMESTAMP NOT NULL DEFAULT NOW()
);

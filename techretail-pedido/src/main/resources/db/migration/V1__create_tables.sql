CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    id_cliente BIGINT NOT NULL,              -- referencia logica
    id_tienda BIGINT,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    subtotal NUMERIC(10,2) NOT NULL,
    igv NUMERIC(10,2) NOT NULL,
    total NUMERIC(10,2) NOT NULL,
    metodo_pago VARCHAR(15),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pedido_items (
    id BIGSERIAL PRIMARY KEY,
    id_pedido BIGINT NOT NULL REFERENCES pedidos(id),
    id_producto BIGINT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    subtotal NUMERIC(10,2) NOT NULL
);

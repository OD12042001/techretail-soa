CREATE TABLE inventarios (
    id BIGSERIAL PRIMARY KEY,
    id_producto BIGINT NOT NULL,
    id_tienda BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 5,
    UNIQUE (id_producto, id_tienda)
);

CREATE TABLE reservas (
    id BIGSERIAL PRIMARY KEY,
    id_pedido BIGINT NOT NULL,
    id_producto BIGINT NOT NULL,
    id_tienda BIGINT NOT NULL,
    cantidad INT NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'RESERVADO',
    fecha TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE alertas (
    id BIGSERIAL PRIMARY KEY,
    id_producto BIGINT NOT NULL,
    id_tienda BIGINT NOT NULL,
    stock_actual INT NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    fecha TIMESTAMP NOT NULL DEFAULT NOW()
);

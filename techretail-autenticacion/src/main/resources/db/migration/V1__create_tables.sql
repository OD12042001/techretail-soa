CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    usuario VARCHAR(120) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    id_rol BIGINT REFERENCES roles(id),
    nombre VARCHAR(120),
    estado VARCHAR(10) DEFAULT 'ACTIVO',
    fecha_registro TIMESTAMP DEFAULT NOW()
);

-- Insertar roles por defecto
INSERT INTO roles (nombre) VALUES ('ADMIN'), ('EMPLEADO'), ('CLIENTE');

CREATE TABLE proveedores_ia (
    id CHAR(36) PRIMARY KEY,
    usuario_id INT NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    clave_cifrada VARBINARY(1100) NOT NULL,
    nonce BINARY(12) NOT NULL,
    kid VARCHAR(40) NOT NULL,
    sufijo VARCHAR(4) NOT NULL,
    modelo VARCHAR(160) NOT NULL,
    context_window INT NOT NULL,
    max_output_tokens INT NOT NULL,
    supports_vision BOOLEAN NOT NULL DEFAULT FALSE,
    supports_json_mode BOOLEAN NOT NULL DEFAULT FALSE,
    max_concurrency INT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    ultima_prueba DATETIME(6) NULL,
    latencia_ms BIGINT NULL,
    consentimiento DATETIME(6) NULL,
    creado_en DATETIME(6) NOT NULL,
    INDEX ix_proveedores_ia_usuario (usuario_id),
    CONSTRAINT fk_proveedores_ia_usuario FOREIGN KEY (usuario_id) REFERENCES auth_user(id) ON DELETE CASCADE
);

CREATE TABLE preferencias_usuario (
    usuario_id INT PRIMARY KEY,
    proveedor_id CHAR(36) NULL,
    version INT NOT NULL DEFAULT 1,
    preferencias JSON,
    CONSTRAINT fk_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_preferencias_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores_ia(id) ON DELETE SET NULL
);

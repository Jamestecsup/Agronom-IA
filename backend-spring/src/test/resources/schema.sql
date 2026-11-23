CREATE TABLE imagenes_planta (
    id CHAR(36) NOT NULL PRIMARY KEY,
    batch_id CHAR(36) NOT NULL,
    relative_path VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_imagenes_planta_lote (batch_id),
    INDEX idx_imagenes_planta_fecha (created_at)
);

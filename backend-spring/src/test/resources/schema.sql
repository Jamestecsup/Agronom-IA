CREATE TABLE IF NOT EXISTS plant_images (
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
    INDEX idx_plant_images_lote (batch_id),
    INDEX idx_plant_images_fecha (created_at)
);

CREATE TABLE IF NOT EXISTS plant_identifications (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT,
    batch_id CHAR(36) NOT NULL,
    scientific_name VARCHAR(255) NOT NULL,
    common_name VARCHAR(255),
    family VARCHAR(255),
    confidence DOUBLE NOT NULL,
    result_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS identification_runs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT,
    batch_id CHAR(36) NOT NULL,
    organ VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    raw_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id CHAR(32) NOT NULL PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_progress_images (
    id CHAR(32) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plant_id CHAR(32) NOT NULL,
    message_id CHAR(32) NOT NULL,
    relative_path VARCHAR(512) NOT NULL UNIQUE,
    mime_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    caption CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

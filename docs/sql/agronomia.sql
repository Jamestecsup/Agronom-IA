-- Base de datos compartida (Django + Spring Boot admin).
-- Solo se crea aquí la tabla legada `plant_images`; las tablas nuevas
-- (plant_identifications, chat_messages, profile) las crea Django con
-- `py manage.py migrate`.

CREATE DATABASE IF NOT EXISTS agronomia
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE agronomia;

CREATE TABLE IF NOT EXISTS plant_images (
  id CHAR(36) NOT NULL PRIMARY KEY,
  batch_id CHAR(36) NOT NULL,
  relative_path VARCHAR(255) NOT NULL UNIQUE,
  mime_type VARCHAR(32) NOT NULL,
  file_size BIGINT UNSIGNED NOT NULL,
  width INT UNSIGNED NOT NULL,
  height INT UNSIGNED NOT NULL,
  processing_status ENUM('pending', 'processed', 'failed') NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_plant_images_batch (batch_id),
  INDEX idx_plant_images_created (created_at)
) ENGINE=InnoDB;

-- Cuenta de API local (ajusta la contraseña fuera del repositorio):
-- CREATE USER 'agronomia_api'@'127.0.0.1' IDENTIFIED BY 'cambia-esto';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON agronomia.* TO 'agronomia_api'@'127.0.0.1';

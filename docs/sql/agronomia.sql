-- Base de datos compartida (Django + Spring Boot admin).
-- Solo se crea aquí la tabla legada `plant_images`; las tablas nuevas
-- (core_plantidentification, core_chatmessage, core_profile,
-- core_identificationrun) las crea Django con `py manage.py migrate`.

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

-- Cuenta de la API para desarrollo local (XAMPP, solo 127.0.0.1).
-- La misma contraseña va en backend-django/.env (DB_PASSWORD) y en las
-- variables DB_USERNAME/DB_PASSWORD de Spring. Cámbiala en un equipo real.
CREATE USER IF NOT EXISTS 'agronomia_api'@'127.0.0.1' IDENTIFIED BY 'agronomia_local_2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON agronomia.* TO 'agronomia_api'@'127.0.0.1';
FLUSH PRIVILEGES;

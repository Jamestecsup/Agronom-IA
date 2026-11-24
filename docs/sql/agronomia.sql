-- MySQL Server: inicialización como root mediante el cliente interactivo.
-- Sustituye los dos marcadores por contraseñas distintas durante la configuración.
-- No guardes contraseñas reales en este archivo versionado.

CREATE DATABASE IF NOT EXISTS agronomia
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE agronomia;

-- Django agrega user_id y luego renombra plant_images a imagenes_planta.
-- Si la tabla final ya existe, no recrees la tabla heredada.
SET @bootstrap_images = IF(
  EXISTS (SELECT 1 FROM information_schema.tables
          WHERE table_schema = DATABASE() AND table_name = 'imagenes_planta'),
  'DO 0',
  'CREATE TABLE IF NOT EXISTS plant_images (
  id CHAR(36) NOT NULL PRIMARY KEY,
  batch_id CHAR(36) NOT NULL,
  relative_path VARCHAR(255) NOT NULL UNIQUE,
  mime_type VARCHAR(32) NOT NULL,
  file_size BIGINT UNSIGNED NOT NULL,
  width INT UNSIGNED NOT NULL,
  height INT UNSIGNED NOT NULL,
  processing_status ENUM(''pending'', ''processed'', ''failed'') NOT NULL DEFAULT ''pending'',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_plant_images_batch (batch_id),
  INDEX idx_plant_images_created (created_at)
) ENGINE=InnoDB'
);
PREPARE bootstrap_images FROM @bootstrap_images;
EXECUTE bootstrap_images;
DEALLOCATE PREPARE bootstrap_images;

-- Cuentas dedicadas locales. Los permisos se restablecen al aprovisionarlas.
CREATE USER IF NOT EXISTS 'agronomia_api'@'127.0.0.1' IDENTIFIED BY 'TU_PASSWORD_MYSQL';
CREATE USER IF NOT EXISTS 'agronomia_admin'@'127.0.0.1' IDENTIFIED BY 'TU_PASSWORD_MIGRACIONES';
ALTER USER 'agronomia_api'@'127.0.0.1' IDENTIFIED BY 'TU_PASSWORD_MYSQL';
ALTER USER 'agronomia_admin'@'127.0.0.1' IDENTIFIED BY 'TU_PASSWORD_MIGRACIONES';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'agronomia_api'@'127.0.0.1', 'agronomia_admin'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON agronomia.* TO 'agronomia_api'@'127.0.0.1';
GRANT ALL PRIVILEGES ON agronomia.* TO 'agronomia_admin'@'127.0.0.1';

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

-- Create a dedicated local API account and set its password outside the APK:
-- CREATE USER 'agronomia_api'@'127.0.0.1' IDENTIFIED BY 'replace-with-a-local-password';
-- GRANT INSERT, UPDATE (processing_status) ON agronomia.plant_images TO 'agronomia_api'@'127.0.0.1';

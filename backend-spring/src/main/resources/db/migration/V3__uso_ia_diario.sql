CREATE TABLE uso_ia_diario (
    usuario_id INT NOT NULL,
    fecha DATE NOT NULL,
    llamadas INT NOT NULL,
    PRIMARY KEY (usuario_id, fecha),
    CONSTRAINT fk_uso_ia_usuario FOREIGN KEY (usuario_id) REFERENCES auth_user(id) ON DELETE CASCADE
);

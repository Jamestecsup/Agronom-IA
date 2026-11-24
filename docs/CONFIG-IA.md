# IA en Agronom-IA

Spring Boot integra Pl@ntNet y el proveedor de IA. El archivo local e ignorado `backend-spring/.env` contiene `PLANTNET_API_KEY`, `AI_API_KEY`, `AI_BASE_URL` y `AI_MODEL`. `AI_VISION_MODEL` permite indicar otro modelo para fotos y PDFs y cae en `AI_MODEL` si se omite.

El resultado de Pl@ntNet se conserva para permitir la confirmación de una candidata sin repetir la consulta. La IA hace una llamada para pistas cuando hay candidatas, y hasta cuatro llamadas normales para la ficha. Primero refina, luego genera las categorías y por último enriquece en paralelo con glosario y materiales. La salida se valida como JSON y el log registra duración, estado y número de intento sin cuerpos ni claves.

Las instrucciones están en `backend-spring/src/main/resources/skills/`. Priorizan español, evidencia y rangos condicionados al clima y la variedad. Cada sección incluye un recordatorio y el cliente presenta el aviso de contenido orientativo.

Utiliza los servidores simulados de `ProviderContractsTest` para probar el cliente. En una prueba real aislada, `AI_MAX_REQUESTS` permite fijar un tope global de llamadas del proceso Spring. El valor predeterminado `0` no limita el proceso.

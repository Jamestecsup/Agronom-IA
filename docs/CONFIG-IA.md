# Configuración de la IA

Toda la configuración del proveedor de IA vive **solo** en `local.properties`
(archivo no versionado, está en `.gitignore`). Esos valores se inyectan como
`BuildConfig` y los consume `com.agronomia.util.Constants`.

| Clave en `local.properties` | Uso |
|---|---|
| `AI_BASE_URL` | URL base del servidor de IA (debe terminar en `/`). |
| `AI_API_KEY` | Clave de API. **Nunca** la subas al repo. |
| `AI_MODEL` | Modelo de chat/visión. |
| `AI_IDENTIFY_PATH` | Ruta del endpoint relativa a la base. |

El endpoint final se arma como `AI_BASE_URL + AI_IDENTIFY_PATH`
(ver `Constants.identifyEndpointUrl()`).

---

## IA ORIGINAL (institucional / red WiFi privada)

Valores para volver a probar contra la IA del instituto cuando estés en su red:

```properties
AI_BASE_URL=http://192.168.17.11:3000/
AI_API_KEY=<la API key original>
AI_MODEL=gpt-4o-mini
AI_IDENTIFY_PATH=v1/chat/completions
```

- Endpoint final: `http://192.168.17.11:3000/v1/chat/completions`
- Solo funciona dentro de la red WiFi privada donde está el servidor.
- Es **HTTP en claro**. En builds `debug` ya está permitido
  (`app/src/debug/AndroidManifest.xml`); en `release` sigue bloqueado por seguridad.
- La API key original se quitó de `local.properties.example` (commit `a720f7e`);
  si la necesitas, está en el historial de git, pero **conviene rotarla**.

---

## Proveedor actual: Google Gemini (compatible con OpenAI)

```properties
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
AI_API_KEY=<tu API key de Gemini>
AI_MODEL=gemini-3.5-flash-lite
AI_IDENTIFY_PATH=chat/completions
```

- Endpoint final: `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`
  (ojo: **sin** el `/v1/` extra que usa OpenAI).

### Elección de modelo (medido con la clave real)

| Modelo | Texto | Imagen (760 KB) | Veredicto |
|---|---|---|---|
| `gemini-3.8-flash` | 86 s (luego 503) | timeout >150 s | Saturado / demasiado lento |
| `gemini-3.5-flash-lite` | **1.6 s** | **17.8 s** | ✅ ELEGIDO (JSON correcto) |
| `gemini-2.5-flash` | 404 | 404 | Retirado por Google |

`gemini-3.8-flash` "piensa" por defecto y además da `503 UNAVAILABLE` por alta
demanda, así que con nuestro timeout de lectura (60 s) fallaría. Por eso el
modelo por defecto es `gemini-3.5-flash-lite`, que identifica correctamente
(ej.: girasol → `Helianthus annuus`, confianza 0.99).

## Otras alternativas (mismo formato)

| Proveedor | `AI_BASE_URL` | `AI_MODEL` ejemplo | `AI_IDENTIFY_PATH` |
|---|---|---|---|
| OpenAI | `https://api.openai.com/` | `gpt-4o-mini` | `v1/chat/completions` |
| Ollama (local) | `http://<IP-PC>:11434/v1/` | `llama3.2-vision` | `chat/completions` |
| LM Studio (local) | `http://<IP-PC>:1234/v1/` | el cargado | `chat/completions` |

---

## Cómo probar rápido (texto o imagen)

Script: `scripts/test-ia.ps1` (lee la key de `local.properties`, nunca la imprime).

```powershell
# Prueba de texto
powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -Prompt "Dime el nombre científico del girasol"

# Prueba con imagen
powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -ImagePath "C:\ruta\planta.jpg"
```

## Cómo cambiar de proveedor (para una IA que lea esto)

1. Edita las 4 claves en `local.properties`.
2. Recompila (`./gradlew assembleDebug` o Run en Android Studio).
3. No hace falta tocar código: el cliente ya es OpenAI-compatible.

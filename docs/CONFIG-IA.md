# Configuración de la app

La app identifica plantas en **dos pasos**:

1. **Pl@ntNet** identifica la **especie** a partir de la imagen.
2. **Gemini** (solo texto) genera la **información** de esa especie
   (descripción, usos, cuidados).

Si Pl@ntNet no da una coincidencia confiable, se usa **Gemini visión** como
respaldo. Si en ese respaldo Gemini no alcanza la certeza exigida (100 % por
defecto), se muestra un error pidiendo al usuario **más imágenes** (flor, hoja,
tallo).

## Dónde se configura

Todo vive **solo** en `local.properties` (no versionado, está en `.gitignore`).
Los valores se inyectan como `BuildConfig` y los consume
`com.agronomia.util.Constants`.

| Clave | Uso |
|---|---|
| `AI_BASE_URL` | URL base de la IA de texto/visión (Gemini/OpenAI). |
| `AI_API_KEY` | Clave de la IA de texto/visión. |
| `AI_MODEL` | Modelo de la IA de texto/visión. |
| `AI_IDENTIFY_PATH` | Ruta del endpoint de chat relativa a la base. |
| `PLANTNET_BASE_URL` | URL base de Pl@ntNet. |
| `PLANTNET_API_KEY` | Clave privada de Pl@ntNet. |
| `PLANTNET_PROJECT` | Flora/proyecto (`all` por defecto). |
| `PLANTNET_LANG` | Idioma de los nombres comunes (`es`). |

---

## Pl@ntNet (identificación de especie) — PRIMARIA

```properties
PLANTNET_BASE_URL=https://my-api.plantnet.org/
PLANTNET_API_KEY=<tu key de Pl@ntNet>
PLANTNET_PROJECT=all
PLANTNET_LANG=es
```

- Endpoint: `POST {PLANTNET_BASE_URL}v2/identify/{project}?api-key=...&lang=...&nb-results=...`
- Cuerpo **multipart**: `images` (JPEG) + `organs=auto`.
- Genera/consulta tu key en https://my.plantnet.org/settings/api-key
- **La api-key viaja en la URL**, por eso Pl@ntNet usa un cliente OkHttp **sin
  logging** (ver `NetworkModule`) para no filtrarla en logcat.

## Google Gemini (información / respaldo visión)

```properties
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
AI_API_KEY=<tu key de Gemini>
AI_MODEL=gemini-3.5-flash-lite
AI_IDENTIFY_PATH=chat/completions
```

- Endpoint final: `.../openai/chat/completions` (ojo: **sin** el `/v1/` de OpenAI).
- Modelo probado: `gemini-3.5-flash-lite` (rápido y estable). `gemini-3.8-flash`
  resultó lento/saturado (503) y con nuestro timeout fallaría.

---

## IA ORIGINAL (institucional / red WiFi privada)

Para volver a probar contra la IA del instituto cuando estés en su red:

```properties
AI_BASE_URL=http://192.168.17.11:3000/
AI_API_KEY=<la API key original>
AI_MODEL=gpt-4o-mini
AI_IDENTIFY_PATH=v1/chat/completions
```

- Endpoint final: `http://192.168.17.11:3000/v1/chat/completions`
- Es **HTTP en claro**: en builds `debug` ya está permitido
  (`app/src/debug/AndroidManifest.xml`); en `release` sigue bloqueado.
- La key original se quitó de `local.properties.example` (commit `a720f7e`);
  si la necesitas está en el historial de git, pero **conviene rotarla**.

## Otras alternativas de IA (mismo formato OpenAI)

| Proveedor | `AI_BASE_URL` | `AI_MODEL` ejemplo | `AI_IDENTIFY_PATH` |
|---|---|---|---|
| OpenAI | `https://api.openai.com/` | `gpt-4o-mini` | `v1/chat/completions` |
| Ollama (local) | `http://<IP-PC>:11434/v1/` | `llama3.2-vision` | `chat/completions` |
| LM Studio (local) | `http://<IP-PC>:1234/v1/` | el cargado | `chat/completions` |

---

## Umbrales (en `Constants.kt`)

- `PLANTNET_MIN_CONFIDENCE` (0.2): si la mejor coincidencia de Pl@ntNet queda por
  debajo, se usa el respaldo con Gemini visión.
- `GEMINI_FALLBACK_MIN_CONFIDENCE` (1.0 = 100 %): en el respaldo, si Gemini no
  llega a esta certeza, se pide al usuario más imágenes.

## Cómo probar rápido (texto o imagen)

Script: `scripts/test-ia.ps1` (lee la key de `local.properties`, nunca la imprime).

```powershell
# Prueba de texto contra la IA
powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -Prompt "Dime el nombre cientifico del girasol"

# Prueba con imagen
powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -ImagePath "C:\ruta\planta.jpg"
```

## Cómo cambiar de proveedor (para una IA que lea esto)

1. Edita las claves en `local.properties`.
2. Recompila (`./gradlew assembleDebug` o Run en Android Studio).
3. El cliente de IA ya es OpenAI-compatible; no hay que tocar código.

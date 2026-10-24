# Configuración de IA

La inteligencia artificial corre **solo en el backend Django**
(`backend-django/`). La app Android y las dos webs no tienen claves: piden la
ficha ya armada al backend. Así las claves nunca viajan en un APK ni en el
JavaScript de una web.

## Dónde se configura

Todo vive en `backend-django/.env`, que está en `.gitignore`. `.env.example`
muestra los nombres de las variables:

| Variable | Uso |
|---|---|
| `PLANTNET_API_KEY` | Clave de Pl@ntNet (identificación de especie). |
| `PLANTNET_BASE_URL` | URL base; por defecto `https://my-api.plantnet.org/`. |
| `PLANTNET_PROJECT` | Flora/proyecto (`all`). |
| `PLANTNET_LANG` | Idioma de los nombres comunes (`es`). |
| `AI_API_KEY` | Clave de Gemini. |
| `AI_BASE_URL` | Endpoint compatible con OpenAI (Gemini por defecto). |
| `AI_MODEL` | Modelo de texto (`gemini-3.5-flash-lite`). |
| `PLANT_UPLOAD_DIR` | Carpeta donde se guardan las fotos subidas. |

Para arrancar sin MySQL: `USE_SQLITE=1` en el `.env`.

## Pipeline (skills en `backend-django/skills/`)

Cada paso es una **skill**: un archivo Markdown con las instrucciones para la
IA. `core/skills.py` las lee en cada petición, así que se pueden editar sin
recompilar; el proceso toma los cambios en la siguiente solicitud.

1. **`refine-prompt.md`** — con la ficha de Pl@ntNet genera un prompt de
   investigación (interno, no se muestra).
2. **`categories/*.md`** — una skill por categoría: descripción, luz, riego,
   suelo, clima, floración, usos y cuidados. Las ocho se envían **juntas** en
   una sola llamada, cada una con sus datos concretos (horarios, cantidades,
   medidas).
3. **`glossary.md`** — elige las palabras difíciles de cada categoría y las
   explica en contexto. En la app y en la web salen resaltadas y al pulsarlas
   se abre su significado.
4. **`materials.md`** — materiales, productos y alternativas para las
   categorías de cuidado.
5. **`chat-assistant.md`** — personalidad y reglas del chat posterior.

**Cuatro llamadas por ficha** (no 22): refine → categorías → glosario →
materiales. `core/services/gemini.py` reintenta ante 429 respetando el
`retryDelay` que devuelve la API.

La guía de «cómo tomar mejores fotos» es texto de interfaz (vive en la app y
repite el mismo consejo), no una skill.

## Flujo y umbrales

`core/services/pipeline.py` decide según el score de Pl@ntNet:

| Confianza | Resultado | Qué hace la app |
|---|---|---|
| ≥ 0.50 | `identified` | Genera la ficha completa (8 secciones). |
| 0.20 – 0.49 | `ambiguous` | Muestra las candidatas; al elegir una, `/confirm` reutiliza el JSON guardado de Pl@ntNet y genera la ficha sin volver a identificar. |
| < 0.20 | `unidentified` | Explica el caso y da la guía de fotos. |

Estados adicionales:

| Situación | Resultado | Qué hace la app |
|---|---|---|
| Especie identificada, pero la IA no conoce sus cuidados | `insufficient` | Muestra «Información insuficiente», el nombre de la planta y ofrece otra foto. |
| Falla la IA o la red | `failed` | Mensaje claro con reintento (no una ficha vacía). |

Los valores son `MIN_CONFIDENCE` y `CONFIDENT_CONFIDENCE` en `pipeline.py`.
Cuando la IA devuelve `"unknown"` en una categoría, se trata como vacía; esa
sección muestra un **texto de respaldo** con pautas generales
(`FALLBACK_TEXTS`), así la ficha conserva sus 8 secciones y nunca aparece la
palabra «unknown».

## Pl@ntNet

- Endpoint: `POST {PLANTNET_BASE_URL}v2/identify/{project}?api-key=...&lang=...&nb-results=10`.
- Cuerpo **multipart**: una parte `images` (JPEG) y una parte `organs` por cada
  foto (hasta 5 de la misma planta: flor, hoja, tallo), emparejadas por orden.
- Un 404 *Species not found* se trata como «sin resultados» (estado
  `unidentified`), no como error del servidor.
- La clave viaja en la URL: no se registra en logs ni se devuelve al cliente.
- Genera tu clave en https://my.plantnet.org/settings/api-key

## Gemini

- `AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/`
  (ojo: **sin** el `/v1/` de OpenAI).
- Endpoint final: `.../openai/chat/completions`.
- Modelo probado: `gemini-3.5-flash-lite`. Es rápido y estable; modelos más
  grandes tardaron o devolvieron 503.
- Las respuestas de ficha se piden en **JSON** (`response_format`); el chat
  responde texto Markdown.

## Chat

`POST /agronomia-api/api/chat` recibe `message`, `session_id` y un `context`
opcional con la planta detectada. Django aplica `chat-assistant.md`, recupera
el historial de la sesión desde `core_chatmessage` y responde en español. La
app móvil y la web de usuario renderizan el Markdown con títulos, viñetas,
negrita y código.

## Alternativas de proveedor (documentadas, no implementadas)

El cliente es compatible con OpenAI; para cambiar de proveedor bastaría editar
`AI_BASE_URL` y `AI_MODEL` en `.env` y reiniciar Django:

| Proveedor | `AI_BASE_URL` | `AI_MODEL` de ejemplo |
|---|---|---|
| OpenAI | `https://api.openai.com/` | `gpt-4o-mini` |
| Ollama (local) | `http://<IP-PC>:11434/v1/` | `llama3.2-vision` |
| LM Studio (local) | `http://<IP-PC>:1234/v1/` | el modelo cargado |
| Qwen local | `http://<IP-PC>:8000/v1/` | `qwen-3.6` |

En esta entrega solo está probado Gemini: las demás filas son referencia.

## IA institucional (red privada)

Si se prueba en la red del instituto:

```properties
AI_BASE_URL=http://192.168.17.11:3000/v1/
AI_MODEL=gpt-4o-mini
```

- Es **HTTP en claro**: `app/src/debug/AndroidManifest.xml` lo permite en
  builds de desarrollo; la build de release lo bloquea y exige HTTPS (igual que
  un backend Django local).
- La clave original se quitó de `local.properties.example` en el commit
  `a720f7e`, pero sigue en el historial de Git: **conviene rotarla**.

## Cómo probar

Antes de la demo, arranca MySQL, Django, Spring y las dos webs, y comprueba
que responden:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-servicios.ps1
```

Pruebas automáticas del pipeline (sin gastar cuota, con Pl@ntNet y Gemini
simulados):

```powershell
cd backend-django
py manage.py test core.tests
```

Prueba final con cuota de IA (con las fotos de la galería del emulador):

1. **Identificada**: girasol nítido → ficha de 8 secciones, consejos generales
   y glosario.
2. **Con dudas**: foto recortada → candidatas; elige una → ficha sin volver a
   llamar a Pl@ntNet.
3. **Sin detección**: imagen gris → mensaje y guía de fotos.
4. **Información insuficiente**: si la IA no conoce la especie, aparece el
   aviso con el nombre y no una ficha vacía (cubierto por las pruebas).
5. **Chat**: pregunta por el riego con la planta detectada y verifica que la
   respuesta es de esa especie.
6. **Panel admin**: la identificación y el mensaje aparecen en Spring
   (http://localhost:5173).

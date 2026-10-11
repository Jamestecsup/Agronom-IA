# Configuración de la app

La app identifica plantas con este flujo:

1. **Pl@ntNet** identifica la **especie** y devuelve su ficha extendida: mejor
   coincidencia (nombre científico, autoría, todos los nombres comunes, género,
   familia, score) + hasta 3 especies candidatas alternativas + IDs
   GBIF/POWO/IUCN (vienen a nivel de resultado, no dentro de `species`).
2. **La IA (paso 1)** genera un **prompt optimizado de investigación** a partir
   de esa ficha (interno, no se muestra).
3. **La IA (paso 2)** ejecuta **UN prompt por categoría de cuidado, en paralelo**
   (descripción, luz, riego, suelo, clima, floración, usos, cuidados). Cada uno
   recibe la ficha extendida + el prompt de investigación y devuelve el texto
   extenso de SU categoría en JSON `{"text": "..."}`, con datos concretos
   (horarios, frecuencias, cantidades, medidas). Nada de consejos vagos.
4. **La IA (paso 3)** genera el **glosario contextual** de cada categoría, en
   paralelo: con el texto ya generado elige las palabras difíciles y explica
   cada una de forma sencilla DENTRO DEL CONTEXTO (`{"terms": [{"word",
   "meaning"}]}`). En la app esas palabras salen resaltadas y al tocarlas se
   abre su significado.
5. **La IA (paso 4)** lista **materiales y alternativas** solo en categorías de
   cuidado (luz, riego, suelo, clima, cuidados), en paralelo: productos y
   herramientas con marcas de ejemplo más alternativas viables si la estación
   no acompaña (p. ej. forzado en ambiente controlado)
   (`{"items": [{"name", "detail"}]}`). Se muestran en tarjetas de tonos que
   combinan con el color esencial de su categoría.

Si Pl@ntNet no da una coincidencia confiable, se usa **la IA de visión** como
respaldo (la cadena se aplica igual, con la información disponible).
Si en ese respaldo la IA no alcanza la certeza exigida (100 % por defecto), se
muestra un error pidiendo al usuario **más imágenes** (flor, hoja, tallo).

## Dónde se configura

Todo vive **solo** en `local.properties` (no versionado, está en `.gitignore`).
Los valores se inyectan como `BuildConfig` y los consume
`com.agronomia.util.Constants`.

| Clave | Uso |
|---|---|
| `AI_BASE_URL` | URL base de la IA de texto/visión (compatible OpenAI: Gemini, Qwen 3.6 local...). |
| `AI_API_KEY` | Clave de la IA de texto/visión (si el servidor la exige). |
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
  (`nb-results=10`; la app usa la mejor + hasta 3 alternativas para la ficha).
- Cuerpo **multipart**: UNA parte `images` (JPEG) + UNA parte `organs` POR
  CADA foto (hasta 5 de la misma planta: flor, hoja, tallo), emparejadas por orden.
  El órgano de cada foto lo elige el usuario en su tarjeta (auto por defecto).
  El respaldo con IA de visión usa solo la primera foto.
- Genera/consulta tu key en https://my.plantnet.org/settings/api-key
- **La api-key viaja en la URL**, por eso Pl@ntNet usa un cliente OkHttp **sin
  logging** (ver `NetworkModule`) para no filtrarla en logcat.

## Google Gemini (información / respaldo visión) — ACTUAL

```properties
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
AI_API_KEY=<tu key de Gemini>
AI_MODEL=gemini-3.5-flash-lite
AI_IDENTIFY_PATH=chat/completions
```

- Endpoint final: `.../openai/chat/completions` (ojo: **sin** el `/v1/` de OpenAI).
- Modelo probado: `gemini-3.5-flash-lite` (rápido y estable). `gemini-3.8-flash`
  resultó lento/saturado (503) y con nuestro timeout fallaría.
- Se usa porque habla el **mismo formato OpenAI** que la IA privada oficial;
  cuando estés en su red, solo cambia `local.properties` (sin tocar código).

## Qwen 3.6 local / IA privada oficial (cuando estés en su red)

```properties
# Ejemplo (ajusta a tu servidor):
# AI_BASE_URL=http://192.168.17.11:3000/
# AI_API_KEY=<la API key del servidor, si exige>
# AI_MODEL=qwen-3.6 (o el nombre que exponga el servidor)
# AI_IDENTIFY_PATH=v1/chat/completions
```

- El cliente ya es OpenAI-compatible (texto + visión por `image_url` + JSON);
  no hay que tocar código, solo `local.properties` + recompilar.
- La visión (respaldo) envía la imagen ya preparada como JPEG Base64
  (`ImageUtils.uriToJpegBytes` → `toBase64Jpeg`).

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

## Mejoras de detección (pendiente, para después)

Ideas acordadas para cuando Pl@ntNet no identifica con certeza:

1. **Top-3 elegible**: si hay 2-3 especies cercanas en score, mostrarlas para
   que el agricultor elija (con foto y score).
   Cada candidata trae foto de referencia de internet (Wikimedia Commons, sin
   clave, vía Coil) y hasta 3 nombres comunes de ejemplo.
2. **Chequeo de calidad previo**: avisar antes de enviar si la foto está
   oscura o movida, y guiar (más luz, acercarse).
3. **Selector de órgano**: elegir hoja/flor/fruto por foto en vez de `auto`
   (implementado: una tarjeta de órgano con su descripción por cada foto).
4. **Ajustar umbrales**: bajar el umbral del respaldo o votar entre candidatas.
5. **Guía ligada**: las opciones del top-3 van ligadas al chequeo de calidad y
   al selector de órgano, explicando QUÉ foto tomar (flor, hoja, tallo) con
   palabras resaltadas y significado en contexto, como el glosario — para
   subir la chance de detectar bien.

## Umbrales (en `Constants.kt`)

Regla acordada:

- **20% o menos** = sin detección: guía de fotos + tomar otra foto.
- **21% a 49%** = dudas: similares con foto y nombres para elegir.
- **50% o más** = validada: ficha completa con su % visible.

- `PLANTNET_MIN_CONFIDENCE` (0.2): si la mejor coincidencia de Pl@ntNet queda por
  debajo, se usa el respaldo con IA de visión.
  Regla acordada: **20% o menos = error o desambiguación** (visión → candidatas
  para elegir → error pidiendo más fotos); **21% o más = resultado con su %
  visible** (el % muestra si está validada o con dudas).
- `AI_FALLBACK_MIN_CONFIDENCE` (1.0 = 100 %): en el respaldo, si la IA no
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

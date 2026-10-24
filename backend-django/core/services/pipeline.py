"""Pipeline de identificación + información (Django).

Orquesta: Pl@ntNet (identificar) → Gemini refine → categorías → glosario
→ materiales. Las skills se cargan en runtime desde `skills/` (core.skills).

Para respetar la cuota gratuita se hacen 4 llamadas a la IA (no 22): las
categorías, el glosario y los materiales se agrupan en una llamada cada uno.
"""
from django.conf import settings

from core import skills
from core.services import gemini, plantnet

CATEGORIES = [
    "description", "light", "watering", "soil",
    "climate", "flowering", "uses", "care",
]
CATEGORY_TITLES = {
    "description": "Descripción",
    "light": "Luz",
    "watering": "Riego",
    "soil": "Suelo",
    "climate": "Clima",
    "flowering": "Floración",
    "uses": "Usos",
    "care": "Cuidados",
}
MATERIAL_CATEGORIES = ["light", "watering", "soil", "climate", "care"]

# Umbrales de confianza (regla acordada con el usuario).
MIN_CONFIDENCE = 0.20        # menos de 20%: sin detección
CONFIDENT_CONFIDENCE = 0.50  # 50%+ = validada; 20-49% = dudas

# Valores con los que la IA dice que no conoce la especie.
UNKNOWN_VALUES = {
    "unknown", "desconocido", "desconocida", "no disponible", "n/a",
    "no se conoce", "sin informacion", "sin información",
}

# Texto de respaldo por categoría: se usa cuando la IA no aporta datos de esa
# categoría, para no dejar huecos ni mostrar la palabra "unknown" al usuario.
FALLBACK_TEXTS = {
    "description": "No encontramos una descripción fiable para esta especie.",
    "light": (
        "No hay datos específicos. En general, dale luz natural abundante y "
        "evita el sol fuerte del mediodía."
    ),
    "watering": (
        "No hay datos específicos. Riega cuando el sustrato esté seco al tacto "
        "y evita encharcamientos."
    ),
    "soil": (
        "No hay datos específicos. Usa un sustrato suelto, con materia orgánica "
        "y buen drenaje."
    ),
    "climate": (
        "No hay datos específicos. Protégela de heladas y de cambios bruscos "
        "de temperatura."
    ),
    "flowering": "No hay datos específicos sobre su floración.",
    "uses": "No hay datos específicos sobre sus usos.",
    "care": (
        "No hay datos específicos. Revisa hojas y tallos cada semana, retira "
        "las partes secas y controla las plagas a tiempo."
    ),
}

# Mensaje cuando la especie se identificó pero la IA no tiene datos de cuidado.
INSUFFICIENT_MESSAGE = (
    "Identificamos la especie, pero no encontramos información suficiente "
    "sobre sus cuidados. Prueba con otra foto o pregunta al asistente."
)


class InsufficientInfoError(RuntimeError):
    """La especie se identificó, pero la IA no tiene datos de cuidado."""


def _model():
    return settings.AI_MODEL, settings.AI_API_KEY, settings.AI_BASE_URL


def _clean_text(value):
    """Normaliza el texto de la IA: "unknown" significa que no conoce la planta."""
    text = str(value or "").strip()
    if text.lower().rstrip(".") in UNKNOWN_VALUES:
        return ""
    return text


def _plant_header(result):
    """Datos básicos de la especie: cabecera de la ficha."""
    species = result.get("species") or {}
    commons = species.get("commonNames") or []
    return {
        "common_name": commons[0] if commons else "",
        "scientific_name": (
            species.get("scientificNameWithoutAuthor")
            or species.get("scientificName", "")
        ),
        "authorship": species.get("scientificNameAuthorship", ""),
        "family": (species.get("family") or {}).get(
            "scientificNameWithoutAuthor", ""
        ),
        "confidence": float(result.get("score", 0)),
    }


def build_ficha(result):
    """Texto legible con la ficha de Pl@ntNet (mejor coincidencia + candidatas)."""
    lines = ["Ficha de la planta (mejor coincidencia):"]
    species = result.get("species") or {}
    name = species.get("scientificNameWithoutAuthor") or species.get("scientificName", "")
    if name:
        authorship = species.get("scientificNameAuthorship")
        lines.append(f"- Nombre científico: {name}" + (f" {authorship}" if authorship else ""))
    commons = species.get("commonNames") or []
    if commons:
        lines.append("- Nombres comunes (todos): " + ", ".join(commons))
    genus = (species.get("genus") or {}).get("scientificNameWithoutAuthor")
    if genus:
        lines.append(f"- Género: {genus}")
    family = (species.get("family") or {}).get("scientificNameWithoutAuthor")
    if family:
        lines.append(f"- Familia: {family}")
    lines.append(f"- Similitud (score Pl@ntNet): {int(result.get('score', 0) * 100)}%")
    for key, label in (("gbif", "GBIF ID"), ("powo", "POWO ID")):
        ref = result.get(key) or {}
        if ref.get("id"):
            lines.append(f"- {label}: {ref['id']}")
    iucn = result.get("iucn") or {}
    if iucn.get("category"):
        lines.append(f"- IUCN: {iucn['category']}")
    alternatives = result.get("alternatives") or []
    if alternatives:
        lines.append("Especies candidatas alternativas:")
        for i, alt in enumerate(alternatives, 1):
            text = f"  {i}. {alt.get('scientific_name') or alt.get('scientificName', '')}"
            if alt.get("score") is not None:
                text += f" ({int(alt['score'] * 100)}%)"
            if alt.get("common_name"):
                text += f" — {alt['common_name']}"
            lines.append(text)
    return "\n".join(lines)


def identify(images, organ="auto"):
    """Identifica y genera la ficha completa.

    - {'status': 'identified', 'plant': {...}}   → 50% o más
    - {'status': 'ambiguous', 'candidates': [...]} → 20-49%
    - {'status': 'unidentified', 'message': ...}  → menos de 20%
    - {'status': 'insufficient', 'message': ...}  → identificada, sin datos
    - {'status': 'failed', 'message': ...}        → error de red/servicio
    """
    try:
        data = plantnet.identify(
            images, organ=organ, project=settings.PLANTNET_PROJECT,
            lang=settings.PLANTNET_LANG, api_key=settings.PLANTNET_API_KEY,
            base_url=settings.PLANTNET_BASE_URL,
        )
    except plantnet.PlantNetError as exc:
        return {"status": "failed", "message": str(exc)}

    results = data.get("results") or []
    if not results:
        return {"status": "unidentified", "message": "No se encontraron coincidencias.", "raw": data}

    ranked = sorted(results, key=lambda r: r.get("score", 0), reverse=True)
    best = ranked[0]
    score = float(best.get("score", 0))

    candidates = []
    for r in ranked:
        sp = r.get("species") or {}
        candidates.append({
            "scientific_name": sp.get("scientificNameWithoutAuthor") or sp.get("scientificName", ""),
            "common_names": sp.get("commonNames") or [],
            "family": (sp.get("family") or {}).get("scientificNameWithoutAuthor", ""),
            "confidence": float(r.get("score", 0)),
        })

    if score < MIN_CONFIDENCE:
        return {
            "status": "unidentified",
            "message": "La coincidencia es muy baja. Toma mejores fotos e inténtalo de nuevo.",
            "raw": data,
        }
    if score < CONFIDENT_CONFIDENCE:
        return {"status": "ambiguous", "candidates": candidates, "raw": data}

    try:
        plant = _run_full_pipeline(best)
    except InsufficientInfoError as exc:
        header = _plant_header(best)
        return {
            "status": "insufficient",
            "message": str(exc),
            "plant_name": header["common_name"] or header["scientific_name"],
            "raw": data,
        }
    except gemini.GeminiError as exc:
        return {"status": "failed", "message": str(exc), "raw": data}
    return {"status": "identified", "plant": plant, "candidates": candidates, "raw": data}


def _run_full_pipeline(best):
    """Refine → categorías → glosario → materiales (4 llamadas a la IA)."""
    model, api_key, base_url = _model()
    species = best.get("species") or {}
    ficha = build_ficha(best)
    scientific_name = species.get("scientificNameWithoutAuthor") or species.get("scientificName", "")

    try:
        refined = gemini.refine_prompt(
            ficha, skills.load_skill("refine-prompt"), model, api_key, base_url
        )
    except (gemini.GeminiError, skills.SkillError):
        refined = ""

    try:
        category_skills = {cat: skills.load_category_skill(cat) for cat in CATEGORIES}
        raw_texts = gemini.categories_batch(
            category_skills, ficha, refined, model, api_key, base_url
        )
    except (gemini.GeminiError, skills.SkillError) as exc:
        # Fallo del servicio (cuota, red o skill ausente): no es falta de datos.
        raise gemini.GeminiError(
            "La IA no pudo generar la ficha. Espera unos minutos y vuelve a intentarlo."
        ) from exc

    # "unknown" no es información: se trata como categoría vacía.
    texts = {cat: _clean_text(raw_texts.get(cat)) for cat in CATEGORIES}

    # Si la IA no conoce NINGUNA categoría, no hay ficha que mostrar: se avisa
    # con un estado propio (identificada, pero sin información suficiente).
    if not any(texts.values()):
        raise InsufficientInfoError(INSUFFICIENT_MESSAGE)

    try:
        glossaries = gemini.glossary_batch(
            skills.load_skill("glossary"), texts, model, api_key, base_url
        )
    except (gemini.GeminiError, skills.SkillError):
        glossaries = {cat: [] for cat in CATEGORIES}

    try:
        mats = gemini.materials_batch(
            skills.load_skill("materials"), scientific_name, texts,
            MATERIAL_CATEGORIES, model, api_key, base_url,
        )
    except (gemini.GeminiError, skills.SkillError):
        mats = {cat: [] for cat in MATERIAL_CATEGORIES}

    # Las categorías sin datos muestran un texto de respaldo con pautas
    # generales: la ficha siempre conserva sus 8 secciones.
    sections = [
        {
            "key": category,
            "title": CATEGORY_TITLES[category],
            "body": texts.get(category) or FALLBACK_TEXTS[category],
            "terms": glossaries.get(category, []),
            "materials": mats.get(category, []),
        }
        for category in CATEGORIES
    ]

    plant = _plant_header(best)
    plant["sections"] = sections
    return plant


def confirm(raw, scientific_name):
    """Genera la ficha de la candidata elegida usando el JSON de Pl@ntNet guardado.

    @param raw: JSON crudo de una corrida de Pl@ntNet.
    @param scientific_name: especie elegida por el agricultor.
    @return: dict de la ficha o None si la especie no está en la corrida.
    @raises InsufficientInfoError: si la IA no tiene datos de esa especie.
    """
    results = (raw or {}).get("results") or []
    target = None
    for r in results:
        sp = r.get("species") or {}
        name = sp.get("scientificNameWithoutAuthor") or sp.get("scientificName", "")
        if name.strip().lower() == scientific_name.strip().lower():
            target = r
            break
    if target is None:
        return None
    return _run_full_pipeline(target)

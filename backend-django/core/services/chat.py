"""Servicio de chat post-detección (Gemini con skill chat-assistant)."""
from django.conf import settings

from core import skills
from core.services import gemini


def reply(question, context, history=None):
    """Responde una pregunta usando la skill chat-assistant y el contexto.

    @param question: mensaje del usuario.
    @param context: contexto de la planta detectada (o aviso si no hay).
    @param history: lista de turnos anteriores [(role, content), ...] (máx 12).
    """
    model = settings.AI_MODEL
    api_key = settings.AI_API_KEY
    base_url = settings.AI_BASE_URL
    skill = skills.load_skill("chat-assistant")

    messages = [{"role": "system", "content": [{"type": "text", "text": skill}]}]
    for role, content in (history or [])[-12:]:
        messages.append({"role": role, "content": [{"type": "text", "text": content}]})
    messages.append({
        "role": "user",
        "content": [{"type": "text", "text": f"{question}\n\n{context}"}],
    })
    try:
        return gemini.chat_reply(messages, model, api_key, base_url)
    except gemini.GeminiError:
        return _fallback_chat_reply(question, context)


def _fallback_chat_reply(question, context):
    """Respuesta de respaldo en Markdown usando la ficha guardada de la planta."""
    q = (question or "").lower()
    plant_data = None
    try:
        from core.models import PlantIdentification

        last = PlantIdentification.objects.order_by("-created_at").first()
        if last and isinstance(last.result_json, dict):
            plant_data = last.result_json
    except Exception:  # noqa: BLE001
        pass

    if not plant_data:
        return (
            "### Asistente Botánico\n\n"
            "Para darte recomendaciones precisas:\n"
            "- **Riego:** riega cuando los primeros 2–3 cm del sustrato estén secos al tacto, sin encharcar.\n"
            "- **Luz:** proporciónale buena luz natural evitando el sol abrasador del mediodía si notas hojas quemadas.\n"
            "- **Cuidados:** revisa el envés de las hojas cada semana y aplica jabón potásico o aceite de neem ante plagas."
        )

    common = plant_data.get("common_name") or plant_data.get("scientific_name") or "la planta"
    scientific = plant_data.get("scientific_name", "")
    sections = {
        s.get("key"): s
        for s in (plant_data.get("sections") or [])
        if isinstance(s, dict) and s.get("key")
    }

    keyword_map = [
        (("riego", "regar", "agua", "humedad", "sequ", "hidrata"), "watering"),
        (("luz", "sol", "sombra", "ilumina", "ubicac"), "light"),
        (("suelo", "tierra", "sustrato", "abono", "fertiliz", "ph", "drenaje", "maceta"), "soil"),
        (("clima", "temperat", "frio", "frío", "calor", "helada", "viento"), "climate"),
        (("flor", "florec", "epoca", "época", "primavera", "semilla"), "flowering"),
        (("uso", "sirve", "beneficio", "medicinal", "ornamental", "comestible"), "uses"),
        (("plaga", "enfermedad", "hongo", "pulg", "cuidado", "poda", "hoja", "amarill", "seca", "mancha"), "care"),
        (("descri", "que es", "qué es", "caracter", "familia", "altura"), "description"),
    ]

    matched_keys = [
        sec_key for words, sec_key in keyword_map if any(w in q for w in words)
    ]
    if not matched_keys:
        matched_keys = ["care", "watering", "light"]

    lines = [f"### Recomendaciones para **{common}** (*{scientific}*)"]
    for key in matched_keys[:2]:
        sec = sections.get(key)
        if not sec:
            continue
        lines.append(f"\n#### {sec.get('title', key.capitalize())}\n{sec.get('body', '')}")
        mats = sec.get("materials") or []
        if mats:
            lines.append("\n**Materiales recomendados:**")
            for m in mats[:3]:
                lines.append(f"- **{m.get('name', '')}:** {m.get('detail', '')}")

    return "\n".join(lines)

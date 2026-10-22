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
    return gemini.chat_reply(messages, model, api_key, base_url)

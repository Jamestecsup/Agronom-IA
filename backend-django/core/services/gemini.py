"""Cliente de Gemini (endpoint OpenAI-compatible) con skills.

Para no agotar la cuota gratuita (429 por ráfagas), el pipeline agrupa el
trabajo en pocas llamadas: refine (1), categorías (1), glosario (1) y
materiales (1). Cada categoría sigue teniendo su propia skill en
`skills/categories/`; se envían todas juntas en la misma petición.
"""
import json
import time

import requests

# Reintentos ante 429 (cuota por minuto).
MAX_RETRIES = 2
DEFAULT_BACKOFF = 8  # segundos


class GeminiError(RuntimeError):
    """Error al consultar la IA."""


def _retry_delay(resp, attempt):
    """Segundos a esperar tras un 429 (usa `retryDelay` de la API si existe)."""
    try:
        data = resp.json()
        detail = data if isinstance(data, list) else [data]
        for item in detail:
            for d in (item.get("error", {}) or {}).get("details", []) or []:
                delay = d.get("retryDelay")
                if delay and str(delay).endswith("s"):
                    return float(str(delay)[:-1]) + 1
    except Exception:  # noqa: BLE001
        pass
    return DEFAULT_BACKOFF * (attempt + 1)


def _post(url, headers, body, retries=MAX_RETRIES, timeout=90):
    """POST con reintento y espera ante 429."""
    last_error = ""
    for attempt in range(retries + 1):
        resp = requests.post(url, headers=headers, json=body, timeout=timeout)
        if resp.status_code == 429:
            last_error = "límite de uso de la IA"
            if attempt < retries:
                time.sleep(_retry_delay(resp, attempt))
                continue
            raise GeminiError(
                "La IA alcanzó su límite de uso. Espera unos minutos y vuelve a intentarlo."
            )
        if resp.status_code != 200:
            raise GeminiError(f"IA HTTP {resp.status_code}: {resp.text[:180]}")
        return resp.json()
    raise GeminiError(last_error or "Error de la IA.")


def _parse_json(content):
    """Extrae el primer objeto JSON del texto devuelto por la IA (tolerante)."""
    text = (content or "").strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        raise GeminiError("La IA no devolvió un JSON válido.")
    return json.loads(text[start : end + 1])


def _messages(system_text, user_text):
    return [
        {"role": "system", "content": [{"type": "text", "text": system_text}]},
        {"role": "user", "content": [{"type": "text", "text": user_text}]},
    ]


def _json_call(system_text, user_text, model, api_key, base_url, temperature=0.0):
    if not api_key:
        raise GeminiError("AI_API_KEY no configurada.")
    url = f"{base_url.rstrip('/')}/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    body = {
        "model": model,
        "messages": _messages(system_text, user_text),
        "temperature": temperature,
        "response_format": {"type": "json_object"},
    }
    data = _post(url, headers, body)
    if data.get("error"):
        raise GeminiError(data["error"].get("message", "error de la IA"))
    content = (data.get("choices") or [{}])[0].get("message", {}).get("content", "")
    if not content:
        raise GeminiError("La IA no devolvió contenido.")
    return _parse_json(content)


def refine_prompt(ficha, skill, model, api_key, base_url):
    """Paso 1: prompt de investigación a partir de la ficha de Pl@ntNet."""
    data = _json_call(skill, ficha, model, api_key, base_url)
    return data.get("prompt", "")


def categories_batch(category_skills, ficha, refined, model, api_key, base_url):
    """Paso 2: texto de TODAS las categorías en una sola llamada.

    @param category_skills: dict {categoría: texto de su skill}.
    @return: dict {categoría: texto}.
    """
    keys = list(category_skills.keys())
    system = (
        "Eres botánico experto. Genera la ficha completa de la planta en español.\n"
        "Para cada categoría debes seguir EXACTAMENTE la skill correspondiente.\n\n"
    )
    for key in keys:
        system += f"\n### SKILL DE LA CATEGORÍA `{key}`\n{category_skills[key]}\n"

    system += (
        "\nResponde ÚNICAMENTE con un objeto JSON válido, sin texto adicional ni "
        "bloques de código Markdown, con exactamente estas claves: "
        + ", ".join(f'"{k}"' for k in keys)
        + ". Cada valor es el texto extenso de esa categoría (4 a 6 frases). "
        'Si no conoces la planta, usa "unknown" en cada valor.'
    )
    user = f"Ficha de Pl@ntNet:\n{ficha}"
    if refined:
        user += f"\n\nPrompt de investigación:\n{refined}"

    data = _json_call(system, user, model, api_key, base_url)
    return {key: (data.get(key) or "") for key in keys}


def glossary_batch(skill, texts, model, api_key, base_url):
    """Paso 3: glosario de TODAS las categorías en una sola llamada."""
    keys = [k for k, v in texts.items() if v and v != "unknown"]
    if not keys:
        return {k: [] for k in texts}
    system = (
        skill
        + "\n\nVas a recibir VARIAS categorías a la vez. Devuelve un único JSON con "
        "una clave por categoría, y en cada una la lista de términos:\n"
        + json.dumps({k: "texto de la categoría" for k in keys}, ensure_ascii=False)
        + "\nFormato exacto: {\"<categoría>\": [{\"word\": \"...\", \"meaning\": \"...\"}]}"
    )
    user = "\n\n".join(f"### {k}\n{texts[k]}" for k in keys)
    data = _json_call(system, user, model, api_key, base_url)
    result = {k: [] for k in texts}
    for key in keys:
        value = data.get(key)
        if isinstance(value, list):
            result[key] = value
    return result


def materials_batch(skill, plant_name, texts, categories, model, api_key, base_url):
    """Paso 4: materiales de las categorías de cuidado en una sola llamada."""
    keys = [k for k in categories if texts.get(k) and texts[k] != "unknown"]
    if not keys:
        return {k: [] for k in categories}
    system = (
        skill
        + "\n\nVas a recibir VARIAS categorías a la vez. Devuelve un único JSON con "
        "una clave por categoría, y en cada una la lista de materiales:\n"
        + json.dumps({k: [{"name": "...", "detail": "..."}] for k in keys}, ensure_ascii=False)
        + "\nFormato exacto: {\"<categoría>\": [{\"name\": \"...\", \"detail\": \"...\"}]}"
    )
    user = f"Planta: {plant_name}\n\n" + "\n\n".join(f"### {k}\n{texts[k]}" for k in keys)
    data = _json_call(system, user, model, api_key, base_url)
    result = {k: [] for k in categories}
    for key in keys:
        value = data.get(key)
        if isinstance(value, list):
            result[key] = value
    return result


def chat_reply(messages, model, api_key, base_url, temperature=0.2):
    """Chat post-detección: responde en texto (Markdown), no JSON."""
    if not api_key:
        raise GeminiError("AI_API_KEY no configurada.")
    url = f"{base_url.rstrip('/')}/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    body = {"model": model, "messages": messages, "temperature": temperature}
    data = _post(url, headers, body)
    return ((data.get("choices") or [{}])[0].get("message", {}).get("content", "") or "").strip()

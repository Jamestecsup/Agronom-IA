---
name: category-care
description: Genera los cuidados de la planta para la ficha del usuario.
---

# Skill: cuidados de la planta

Genera los cuidados (4 a 6 frases) de una planta ya identificada.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO los cuidados: fertilización, poda, trasplante, plagas y enfermedades comunes y cómo prevenirlas, con datos concretos y prácticos; nada de consejos vagos. Si la planta solo crece en cierta estación, cierra con una alternativa viable para seguir cuidándola fuera de estación (por ejemplo, forzado en ambiente controlado).

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "cuidados básicos en extenso"}
Si no conoces la planta, responde {"text":"unknown"}.

## Entrada

Ficha de Pl@ntNet + prompt de investigación + nombre de la categoría (`care`).

## Salida

JSON `{"text": "..."}`. Se muestra en la tarjeta "Cuidados" de la ficha.

---
name: category-flowering
description: Genera la floración de la planta para la ficha del usuario.
---

# Skill: floración de la planta

Genera la floración (4 a 6 frases) de una planta ya identificada.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO la floración: época, duración, características de flores/frutos, y cómo favorecerla, con datos concretos y prácticos; nada de consejos vagos.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "floración en extenso"}
Si no conoces la planta, responde {"text":"unknown"}.

## Entrada

Ficha de Pl@ntNet + prompt de investigación + nombre de la categoría (`flowering`).

## Salida

JSON `{"text": "..."}`. Se muestra en la tarjeta "Floración" de la ficha.

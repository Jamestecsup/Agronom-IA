---
name: category-climate
description: Genera el clima ideal de la planta para la ficha del usuario.
---

# Skill: clima de la planta

Genera el clima ideal (4 a 6 frases) de una planta ya identificada.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO el clima: temperatura ideal y límites, humedad, resistencia al frío/calor, y época de siembra o trasplante, con datos concretos y prácticos; nada de consejos vagos.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "clima y temperatura en extenso"}
Si no conoces la planta, responde {"text":"unknown"}.

## Entrada

Ficha de Pl@ntNet + prompt de investigación + nombre de la categoría (`climate`).

## Salida

JSON `{"text": "..."}`. Se muestra en la tarjeta "Clima" de la ficha.

---
name: category-watering
description: Genera los requisitos de riego de la planta para la ficha del usuario.
---

# Skill: riego de la planta

Genera los requisitos de riego (4 a 6 frases) de una planta ya identificada.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO el riego: frecuencia por estación, cantidad (litros), método, drenaje, y señales de exceso o falta de agua, con datos concretos y prácticos; nada de consejos vagos.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "requisitos de riego en extenso"}
Si no conoces la planta, responde {"text":"unknown"}.

## Entrada

Ficha de Pl@ntNet + prompt de investigación + nombre de la categoría (`watering`).

## Salida

JSON `{"text": "..."}`. Se muestra en la tarjeta "Riego" de la ficha.

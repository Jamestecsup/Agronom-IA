---
name: category-soil
description: Genera el suelo ideal de la planta para la ficha del usuario.
---

# Skill: suelo de la planta

Genera el suelo ideal (4 a 6 frases) de una planta ya identificada.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO el suelo: tipo, textura, pH, materia orgánica, drenaje, y maceta o sustrato recomendado, con datos concretos y prácticos; nada de consejos vagos.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "suelo y sustrato ideales en extenso"}
Si no conoces la planta, responde {"text":"unknown"}.

## Entrada

Ficha de Pl@ntNet + prompt de investigación + nombre de la categoría (`soil`).

## Salida

JSON `{"text": "..."}`. Se muestra en la tarjeta "Suelo" de la ficha.

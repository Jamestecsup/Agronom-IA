---
name: materials
description: Genera materiales, productos y alternativas viables para una categoría de cuidado.
---

# Skill: materiales y alternativas

Genera materiales, productos y alternativas viables para UNA categoría de cuidado.

## Instrucciones

Actúas dentro de una app que ayuda a agricultores. Con la planta, la categoría de cuidado y el texto ya generado para esa categoría, lista de 3 a 6 materiales, productos o herramientas concretos, con nombres genéricos y 1 o 2 marcas comerciales de ejemplo como referencia cuando aplique. Incluye también alternativas viables cuando la estación o el clima no acompañen (por ejemplo, forzado de plantas en ambiente controlado, mallas, riego tecnificado). Cada ítem en 1 o 2 frases cortas y prácticas.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta forma:
{"items": [{"name": "nombre del material o alternativa", "detail": "para qué sirve y ejemplo de uso"}]}
Si no hay materiales, responde {"items": []}.

## Entrada

Planta + categoría de cuidado + texto de la categoría.

## Salida

JSON `{"items": [{"name": "...", "detail": "..."}]}`. Se muestran en tarjetas de tonos que combinan con el color de su categoría.

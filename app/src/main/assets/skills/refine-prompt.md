---
name: refine-prompt
description: Genera un prompt de investigación optimizado a partir de la ficha de Pl@ntNet de una planta.
---

# Skill: refine-prompt

Genera un prompt de investigación optimizado a partir de la ficha de Pl@ntNet de una planta.

## Instrucciones

Actúa como experto en botánica y jardinería. Te doy la ficha de identificación de una planta (datos de Pl@ntNet). Genera un prompt optimizado, en español, para investigar ESA planta concreta: descripción, luz, riego, suelo, clima, floración, usos y cuidados; incluye cualquier dato importante para alguien que la tiene o quiere cultivarla.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"prompt": "el prompt de investigación generado"}
No inventes datos de la planta: apóyate solo en la ficha dada y en el nombre.

## Entrada

La ficha de Pl@ntNet (nombre científico, autoría, nombres comunes, género, familia, score, GBIF/POWO/IUCN, candidatas alternativas).

## Salida

JSON `{"prompt": "..."}` que alimenta las skills de categoría, glosario y hints.

---
name: glossary
description: Genera el glosario contextual de una categoría: palabras difíciles con su significado en contexto.
---

# Skill: glossary

Genera el glosario contextual de una categoría de cuidado: palabras difíciles con su significado en contexto.

## Instrucciones

Actúas dentro de una app que ayuda a agricultores sin conocimiento previo. Te doy la planta, la categoría de cuidado y el texto ya generado para esa categoría. Elige de 3 a 8 palabras difíciles de ese texto y explica cada una de forma sencilla y DENTRO DEL CONTEXTO de la categoría (qué significa ahí), en 1 o 2 frases cortas por palabra.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta forma:
{"terms": [{"word": "palabra tal como aparece en el texto", "meaning": "significado sencillo en contexto"}]}
Si no hay palabras difíciles, responde {"terms": []}.

## Entrada

Planta + categoría + texto de la categoría (skill category-info).

## Salida

JSON `{"terms": [{"word": "...", "meaning": "..."}]}`. En la app esas palabras salen resaltadas y al tocarlas se abre su significado.

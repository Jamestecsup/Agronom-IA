---
name: candidate-hints
description: Genera rasgos únicos de cada planta candidata para distinguirlas entre sí, con términos resaltables.
---

# Skill: candidate-hints

Genera rasgos únicos de cada planta candidata para distinguirlas entre sí, con términos resaltables.

## Instrucciones

Actúas dentro de una app que ayuda a agricultores sin conocimiento previo. Te doy una lista de plantas parecidas entre las que el usuario debe elegir la suya. Para CADA una escribe 1 o 2 frases cortas con sus rasgos ÚNICOS para reconocerla frente a las otras: color y forma de flor y hoja, tamaño, tallo. Nada de tecnicismos sin explicar.
Además marca de 2 a 4 palabras difíciles de ese texto, con su significado sencillo DENTRO DEL CONTEXTO (qué significa ahí), en frases cortas.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta forma:
{"hints": [{"name": "nombre científico tal cual te lo dan", "hint": "rasgos distintivos", "terms": [{"word": "palabra tal como aparece", "meaning": "significado sencillo en contexto"}]}]}
Incluye a TODAS las plantas de la lista, sin omitir ninguna: si de alguna sabes poco, describe en 1 frase sus rasgos generales de familia o género.

## Entrada

Lista de plantas candidatas (nombre científico, nombres comunes, familia, score).

## Salida

JSON `{"hints": [{"name": "...", "hint": "...", "terms": [{"word": "...", "meaning": "..."}]}]}`. En la app cada candidata muestra su hint con palabras resaltables.

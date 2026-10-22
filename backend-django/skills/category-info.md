---
name: category-info
description: Genera el texto extenso de UNA categoría de cuidado (descripción, luz, riego, suelo, clima, floración, usos, cuidados) con datos concretos.
---

# Skill: category-info

Genera el texto extenso de UNA categoría de cuidado de una planta, con datos concretos y prácticos.

## Instrucciones

Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de investigación que te dan, explica EN EXTENSO la categoría asignada (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos. Si la planta solo crece en cierta estación, cierra con una alternativa viable para seguir cuidándola fuera de estación (por ejemplo, forzado en ambiente controlado).

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques de código Markdown, con exactamente esta clave:
{"text": "texto extenso de la categoría"}
Si no conoces la planta, responde {"text":"unknown"}.

## Categorías

- **description**: qué es, porte, tallos, hojas, flores/frutos y rasgos para reconocerla.
- **light**: sol directo/semisombra/sombra, horas al día, orientación, qué pasa con luz insuficiente o excesiva.
- **watering**: frecuencia por estación, cantidad, método, drenaje, señales de exceso o falta de agua.
- **soil**: tipo, textura, pH, materia orgánica, drenaje, maceta o sustrato recomendado.
- **climate**: temperatura ideal y límites, humedad, resistencia al frío/calor, época de siembra o trasplante.
- **flowering**: época, duración, características de flores/frutos y cómo favorecerla.
- **uses**: ornamental, alimenticio, medicinal, ecológico u otros, con ejemplos concretos.
- **care**: fertilización, poda, trasplante, plagas y enfermedades comunes y cómo prevenirlas.

## Entrada

Ficha de Pl@ntNet + prompt de investigación (skill refine-prompt) + nombre de la categoría.

## Salida

JSON `{"text": "..."}`.

---
name: candidate-hints
description: Describe cómo reconocer cada especie candidata y qué la distingue de las demás.
---

# Skill: candidate-hints

Ayudas a un agricultor a elegir entre varias especies parecidas cuando la
identificación automática no es concluyente.

## Instrucciones

Recibes una lista de especies candidatas con nombre científico, nombres comunes,
familia y similitud. Para CADA una:

- Escribe 2 o 3 frases en español sencillo (máximo 55 palabras) con sus
  características visibles: flor, hoja, tallo, porte o fruto.
- Explica al menos un rasgo que comparte con las otras candidatas y al menos
  uno que la distinga de ellas (color, forma, tamaño, época o hábitat). Si no
  hay un rasgo común claro, describe una semejanza general sin afirmar que
  todas las especies lo tienen. No inventes: si no estás seguro, usa
  expresiones como "suele" o "puede".
- No repitas la ficha completa ni des consejos de cuidado.
- Elige de 2 a 4 palabras difíciles del texto y explica su significado dentro
  del contexto, como en el glosario de la ficha.

Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin bloques
de código Markdown, con esta forma exacta:

{"<nombre_científico>": {"hint": "...", "terms": [{"word": "...", "meaning": "..."}]}}

Usa como clave el nombre científico EXACTO que recibiste. Si no conoces una
especie, devuelve {"hint": "", "terms": []} para esa especie.

## Entrada

Lista JSON de candidatas.

## Salida

JSON con una clave por nombre científico y, en cada una, la pista y su glosario.

---
name: glossary
description: Genera glosarios contextualizados para varias categorías de una ficha.
---

# Skill: glossary

Recibes el nombre científico y un objeto con el texto de cada categoría. Elige de tres a cinco términos difíciles por categoría, tal como aparecen en el texto. Si hay menos términos pertinentes, usa solo los disponibles. Explica cada término en una frase breve de hasta 15 palabras, en español común. Conserva los nombres científicos y prescinde de anglicismos y palabras inventadas.

Responde únicamente con un objeto JSON. Repite cada clave de categoría recibida y devuelve su lista de pares `word` y `meaning`. Si una categoría no tiene términos, usa `[]`. Ejemplo con dos categorías:

`{"description":[{"word":"rizoma","meaning":"Tallo subterráneo del que nacen brotes."}],"soil":[]}`

Omite afirmaciones medicinales, de toxicidad, de comestibilidad o de consumo seguro. Basa la explicación en la especie recibida y en el texto de cada categoría.

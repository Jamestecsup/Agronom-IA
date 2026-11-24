---
name: materials
description: Genera materiales, productos y alternativas para varias categorías de cuidado.
---

# Skill: materiales y alternativas

Recibes el nombre científico y un objeto con el texto de cada categoría. Sugiere de tres a cuatro materiales o herramientas concretos por categoría, con nombres genéricos. Si hay menos opciones pertinentes, usa solo las disponibles. Explica cada uso en una frase de hasta 15 palabras. Escribe en español común, sin anglicismos ni palabras inventadas.

Responde únicamente con un objeto JSON. Repite cada clave de categoría recibida y devuelve su lista de pares `name` y `detail`. Si una categoría no tiene materiales pertinentes, usa `[]`. Ejemplo con dos categorías:

`{"watering":[{"name":"Regadera","detail":"Permite dirigir el agua hacia la base."}],"soil":[]}`

Omite afirmaciones medicinales, de toxicidad, de comestibilidad o de consumo seguro. Recomienda materiales para la especie recibida y sus condiciones de cultivo.

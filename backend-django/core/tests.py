"""Pruebas del pipeline de identificación (sin llamar a Pl@ntNet ni a la IA).

Se parchean los servicios externos: comprueban la lógica de umbrales, el
manejo de "unknown" y el estado `insufficient` de la HU-7.
"""
from unittest.mock import patch

from django.test import SimpleTestCase

from core.services import pipeline


def _plantnet_result(score, name="Helianthus annuus", common="Girasol"):
    """Respuesta mínima de Pl@ntNet con una sola especie."""
    return {
        "results": [
            {
                "score": score,
                "species": {
                    "scientificNameWithoutAuthor": name,
                    "scientificName": name,
                    "scientificNameAuthorship": "L.",
                    "commonNames": [common],
                    "family": {"scientificNameWithoutAuthor": "Asteraceae"},
                },
            }
        ]
    }


class PipelineTests(SimpleTestCase):
    """Umbrales, categorías vacías y textos de respaldo."""

    def _identify(self, score=0.9, texts=None, categories_error=None):
        """Ejecuta pipeline.identify con Pl@ntNet y Gemini simulados."""
        data = _plantnet_result(score)
        with patch(
            "core.services.pipeline.plantnet.identify", return_value=data
        ), patch(
            "core.services.pipeline.gemini.refine_prompt", return_value=""
        ), patch(
            "core.services.pipeline.gemini.categories_batch",
            return_value=texts or {},
            side_effect=categories_error,
        ), patch(
            "core.services.pipeline.gemini.glossary_batch", return_value={}
        ), patch(
            "core.services.pipeline.gemini.materials_batch", return_value={}
        ):
            return pipeline.identify([b"foto"])

    def test_identificada_genera_ocho_secciones(self):
        texts = {category: f"Texto de {category}." for category in pipeline.CATEGORIES}

        result = self._identify(score=0.9, texts=texts)

        self.assertEqual(result["status"], "identified")
        self.assertEqual(result["plant"]["common_name"], "Girasol")
        self.assertEqual(result["plant"]["scientific_name"], "Helianthus annuus")
        self.assertEqual(len(result["plant"]["sections"]), 8)
        care = next(s for s in result["plant"]["sections"] if s["key"] == "care")
        self.assertEqual(care["title"], "Cuidados")
        self.assertIn("Texto de care.", care["body"])

    def test_todas_las_categorias_unknown_es_insufficient(self):
        texts = {category: "unknown" for category in pipeline.CATEGORIES}

        result = self._identify(score=0.9, texts=texts)

        self.assertEqual(result["status"], "insufficient")
        self.assertEqual(result["plant_name"], "Girasol")
        self.assertIn("información suficiente", result["message"])

    def test_categoria_sin_datos_usa_texto_de_respaldo(self):
        result = self._identify(score=0.9, texts={"care": "Riega cada 3 días."})

        self.assertEqual(result["status"], "identified")
        bodies = {s["key"]: s["body"] for s in result["plant"]["sections"]}
        self.assertEqual(bodies["care"], "Riega cada 3 días.")
        self.assertEqual(bodies["light"], pipeline.FALLBACK_TEXTS["light"])
        self.assertNotIn("unknown", " ".join(bodies.values()).lower())

    def test_score_bajo_es_unidentified(self):
        result = self._identify(score=0.10)

        self.assertEqual(result["status"], "unidentified")
        self.assertIn("message", result)

    def test_score_limite_020_es_ambiguous(self):
        result = self._identify(score=0.20)

        self.assertEqual(result["status"], "ambiguous")
        self.assertEqual(result["candidates"][0]["scientific_name"], "Helianthus annuus")

    def test_score_medio_es_ambiguous(self):
        result = self._identify(score=0.35)

        self.assertEqual(result["status"], "ambiguous")
        self.assertEqual(len(result["candidates"]), 1)

    def test_fallo_de_la_ia_es_failed(self):
        result = self._identify(
            score=0.9, categories_error=pipeline.gemini.GeminiError("cuota agotada")
        )

        self.assertEqual(result["status"], "failed")
        self.assertIn("no pudo generar la ficha", result["message"])

    def test_clean_text_normaliza_unknown(self):
        self.assertEqual(pipeline._clean_text(" Unknown. "), "")
        self.assertEqual(pipeline._clean_text("desconocida"), "")
        self.assertEqual(pipeline._clean_text("Riega poco"), "Riega poco")

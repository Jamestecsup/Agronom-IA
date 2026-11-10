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

    def _identify(self, score=0.9, texts=None, categories_error=None,
                  materials_error=None, candidate_hints=None,
                  candidate_hints_error=None):
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
            "core.services.pipeline.gemini.materials_batch",
            return_value={},
            side_effect=materials_error,
        ), patch(
            "core.services.pipeline.gemini.candidate_hints",
            return_value=candidate_hints or {},
            side_effect=candidate_hints_error,
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

    def test_candidatas_incluyen_pista_y_glosario(self):
        details = {
            "Helianthus annuus": {
                "hint": "Flor amarilla con centro oscuro; hoja áspera.",
                "terms": [{"word": "capítulo", "meaning": "Conjunto de flores."}],
            }
        }

        result = self._identify(score=0.35, candidate_hints=details)

        self.assertEqual(result["status"], "ambiguous")
        candidate = result["candidates"][0]
        self.assertIn("Flor amarilla", candidate["hint"])
        self.assertEqual(candidate["terms"][0]["word"], "capítulo")

    def test_fallo_de_pistas_no_rompe_ambiguous(self):
        result = self._identify(
            score=0.35,
            candidate_hints_error=pipeline.gemini.GeminiError("sin cuota"),
        )

        self.assertEqual(result["status"], "ambiguous")
        self.assertEqual(result["candidates"][0]["hint"], "")
        self.assertEqual(result["candidates"][0]["terms"], [])

    def test_candidate_hints_normaliza_y_limpia(self):
        data = {
            "helianthus annuus": {
                "hint": "  Flor amarilla.  ",
                "terms": [
                    {"word": "capítulo", "meaning": "Conjunto de flores."},
                    {"word": "", "meaning": "sin palabra"},
                    "dato inválido",
                ],
            },
            "Otra": {"hint": "unknown", "terms": "no es una lista"},
        }
        with patch(
            "core.services.gemini._json_call", return_value=data
        ):
            result = pipeline.gemini.candidate_hints(
                [
                    {"scientific_name": "Helianthus annuus"},
                    {"scientific_name": "Otra"},
                ],
                "skill", "modelo", "clave", "http://ia",
            )

        self.assertEqual(result["Helianthus annuus"]["hint"], "Flor amarilla.")
        self.assertEqual(len(result["Helianthus annuus"]["terms"]), 1)
        self.assertEqual(result["Otra"]["hint"], "")
        self.assertEqual(result["Otra"]["terms"], [])

    def test_fallo_de_la_ia_es_failed(self):
        result = self._identify(
            score=0.9, categories_error=pipeline.gemini.GeminiError("cuota agotada")
        )

        self.assertEqual(result["status"], "failed")
        self.assertIn("no pudo generar la ficha", result["message"])

    def test_materiales_fallidos_no_rompen_la_ficha(self):
        """Si la IA devuelve un JSON roto en materiales, la ficha sigue."""
        texts = {category: f"Texto de {category}." for category in pipeline.CATEGORIES}

        result = self._identify(
            score=0.9,
            texts=texts,
            materials_error=pipeline.gemini.GeminiError("JSON inválido"),
        )

        self.assertEqual(result["status"], "identified")
        self.assertEqual(len(result["plant"]["sections"]), 8)
        _, materials = next(
            (s["key"], s["materials"])
            for s in result["plant"]["sections"]
            if s["key"] == "light"
        )
        self.assertEqual(materials, [])

    def test_clean_text_normaliza_unknown(self):
        self.assertEqual(pipeline._clean_text(" Unknown. "), "")
        self.assertEqual(pipeline._clean_text("desconocida"), "")
        self.assertEqual(pipeline._clean_text("Riega poco"), "Riega poco")

    def test_json_invalido_es_error_de_ia(self):
        """Un JSON roto no debe romper la vista con un 500."""
        with self.assertRaises(pipeline.gemini.GeminiError):
            pipeline.gemini._parse_json('{"a": "sin cerrar}')

    def test_json_call_reintenta_una_vez(self):
        responses = [
            {"choices": [{"message": {"content": '{"a": 1,}'}}]},
            {"choices": [{"message": {"content": '{"a": 1}'}}]},
        ]
        with patch(
            "core.services.gemini._post", side_effect=responses
        ) as post, patch("core.services.gemini.time.sleep"):
            data = pipeline.gemini._json_call("s", "u", "m", "clave", "http://ia")

        self.assertEqual(data, {"a": 1})
        self.assertEqual(post.call_count, 2)

    def test_json_call_falla_tras_dos_intentos(self):
        responses = [
            {"choices": [{"message": {"content": '{"a": 1,}'}}]},
            {"choices": [{"message": {"content": '{"a": 2,}'}}]},
        ]
        with patch(
            "core.services.gemini._post", side_effect=responses
        ) as post, patch("core.services.gemini.time.sleep"):
            with self.assertRaises(pipeline.gemini.GeminiError):
                pipeline.gemini._json_call("s", "u", "m", "clave", "http://ia")

        self.assertEqual(post.call_count, 2)

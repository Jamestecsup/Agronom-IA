"""Cargador de skills en runtime.

Las skills son archivos Markdown versionados en `skills/` que describen cada
proceso del pipeline (refine, categorías, glosario, hints, guía) y el chat.
Se leen en cada uso para poder actualizarlas sin recompilar.
"""
import os

from django.conf import settings

# `settings.SKILLS_DIR` apunta a `backend-django/skills/`.
SKILLS_DIR = settings.SKILLS_DIR


class SkillError(RuntimeError):
    """Falta una skill o está vacía."""


def load_skill(name: str) -> str:
    """Carga una skill de `skills/<name>.md` y devuelve su cuerpo.

    Elimina el frontmatter YAML (--- ... ---) si lo hubiera.
    """
    path = os.path.join(SKILLS_DIR, f"{name}.md")
    if not os.path.exists(path):
        raise SkillError(f"Skill no encontrada: {name}")
    with open(path, encoding="utf-8") as fh:
        text = fh.read().strip()
    if text.startswith("---"):
        parts = text.split("---", 2)
        if len(parts) >= 3:
            text = parts[2].strip()
    if not text:
        raise SkillError(f"Skill vacía: {name}")
    return text


def load_category_skill(category: str) -> str:
    """Carga la skill de una categoría (`skills/categories/<category>.md`)."""
    path = os.path.join(SKILLS_DIR, "categories", f"{category}.md")
    if not os.path.exists(path):
        raise SkillError(f"Skill de categoría no encontrada: {category}")
    return load_skill(f"categories/{category}")

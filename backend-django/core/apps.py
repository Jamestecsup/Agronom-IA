from django.apps import AppConfig
import sys


class CoreConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "core"

    def ready(self):
        # Django 4.2 no declara soporte para Python 3.14. En ese runtime, la
        # copia de Context basada en copy(super()) falla en las vistas de
        # changelist del admin. Copia el estado de forma equivalente y local.
        if sys.version_info >= (3, 14):
            from django.template.context import BaseContext

            def copy_context(context):
                duplicate = context.__class__.__new__(context.__class__)
                duplicate.__dict__ = context.__dict__.copy()
                duplicate.dicts = context.dicts[:]
                return duplicate

            BaseContext.__copy__ = copy_context

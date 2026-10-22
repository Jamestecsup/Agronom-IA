"""WSGI config for Agronom-IA backend Django."""
import os

from django.core.wsgi import get_wsgi_application

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "agronomia.settings")

application = get_wsgi_application()

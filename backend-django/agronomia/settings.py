"""Agronom-IA backend Django (servicio de usuario).

Recibe las fotos del móvil, las valida y almacena, y ejecuta el pipeline de
identificación + información usando Pl@ntNet y Gemini con las skills.
"""
import os
import sys

# Usar PyMySQL como driver MySQL (evita dependencias nativas del sistema).
try:
    import pymysql
    pymysql.install_as_MySQLdb()
except ImportError:
    pass

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Cargar variables de entorno desde .env si existe.
try:
    from dotenv import load_dotenv
    load_dotenv(os.path.join(BASE_DIR, ".env"))
except ImportError:
    pass

SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "insecure-dev-key")
DEBUG = os.environ.get("DJANGO_DEBUG", "True").lower() in ("1", "true", "yes")

ALLOWED_HOSTS = [
    h.strip()
    for h in os.environ.get(
        "ALLOWED_HOSTS", "127.0.0.1,localhost,10.0.2.2"
    ).split(",")
    if h.strip()
]

CORS_ALLOWED_ORIGINS = [
    o.strip()
    for o in os.environ.get(
        "CORS_ALLOWED_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000"
    ).split(",")
    if o.strip()
]

INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "django.contrib.sessions",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "core",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF = "agronomia.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {"context_processors": []},
    },
]

WSGI_APPLICATION = "agronomia.wsgi.application"

# --- Base de datos MySQL (XAMPP) ---
# Con USE_SQLITE=1 se usa SQLite (solo para pruebas locales rápidas).
if os.environ.get("USE_SQLITE", "").lower() in ("1", "true", "yes"):
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": os.path.join(BASE_DIR, "db.sqlite3"),
        }
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.mysql",
            "NAME": os.environ.get("DB_NAME", "agronomia"),
            "USER": os.environ.get("DB_USER", "agronomia_api"),
            "PASSWORD": os.environ.get("DB_PASSWORD", ""),
            "HOST": os.environ.get("DB_HOST", "127.0.0.1"),
            "PORT": os.environ.get("DB_PORT", "3306"),
            "OPTIONS": {"charset": "utf8mb4"},
        }
    }

LANGUAGE_CODE = "es-pe"
TIME_ZONE = "America/Lima"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# --- Configuración del pipeline ---
PLANTNET_API_KEY = os.environ.get("PLANTNET_API_KEY", "")
PLANTNET_BASE_URL = os.environ.get(
    "PLANTNET_BASE_URL", "https://my-api.plantnet.org/"
)
PLANTNET_PROJECT = os.environ.get("PLANTNET_PROJECT", "all")
PLANTNET_LANG = os.environ.get("PLANTNET_LANG", "es")

AI_API_KEY = os.environ.get("AI_API_KEY", "")
AI_BASE_URL = os.environ.get(
    "AI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai/"
)
AI_MODEL = os.environ.get("AI_MODEL", "gemini-3.5-flash-lite")

PLANT_UPLOAD_DIR = os.environ.get("PLANT_UPLOAD_DIR", "C:/xampp/plant-images")

# Skills del pipeline (archivos Markdown cargados en runtime).
SKILLS_DIR = os.path.join(BASE_DIR, "skills")

# Límites de subida (compatibles con el móvil: 1-5 fotos, 15 MiB c/u).
MAX_UPLOAD_IMAGES = 5
MAX_IMAGE_BYTES = 15 * 1024 * 1024

REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
    ],
    "UNAUTHENTICATED_USER": None,
}

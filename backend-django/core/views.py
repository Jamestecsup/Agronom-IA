"""Vistas del backend Django (servicio de usuario).

Endpoints:
  GET  /agronomia-api/api/health      → estado del servicio y de la base
  POST /agronomia-api/api/images.php  → subir 1-5 fotos (contrato de Fernando)
  POST /agronomia-api/api/identify    → pipeline Pl@ntNet + Gemini (con skills)
  POST /agronomia-api/api/confirm     → ficha de la candidata elegida
  POST /agronomia-api/api/chat        → chat post-detección
  POST /agronomia-api/api/register    → autorregistro (rol user)
  POST /agronomia-api/api/login       → validación de credenciales
"""
import os
import uuid

from django.conf import settings
from django.http import JsonResponse
from PIL import Image, UnidentifiedImageError

from core.models import ChatMessage, IdentificationRun, PlantIdentification, PlantImage
from core.services import chat as chat_service
from core.services import gemini, pipeline

# Formatos aceptados (por contenido, no por extensión).
ALLOWED_MIME = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGES = settings.MAX_UPLOAD_IMAGES
MAX_BYTES = settings.MAX_IMAGE_BYTES
MIN_DIMENSION = 16
MAX_DIMENSION = 4096


def _error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


def health(request):
    """GET /agronomia-api/api/health — comprueba servicio y conexión a la base.

    No llama a Pl@ntNet ni a la IA: sirve para saber si el backend está listo
    antes de una demo o para un chequeo de despliegue.
    """
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")

    from django.db import connection

    database_ok = True
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except Exception:  # noqa: BLE001 - cualquier fallo de base cuenta como caída
        database_ok = False

    return JsonResponse({
        "success": database_ok,
        "status": "ok" if database_ok else "degraded",
        "service": "agronomia-api",
        "database": database_ok,
    })


def _validate_image(uploaded):
    """Valida contenido, formato y dimensiones. Devuelve (bytes, mime, w, h)."""
    data = uploaded.read()
    if not data:
        raise ValueError("Archivo vacío")
    if len(data) > MAX_BYTES:
        raise ValueError(f"Archivo mayor a {MAX_BYTES // (1024 * 1024)} MiB")

    try:
        img = Image.open(__import__("io").BytesIO(data))
        img.verify()
    except (UnidentifiedImageError, Exception) as exc:
        raise ValueError("Formato no soportado o imagen corrupta") from exc

    # Reabrir para dimensiones (verify() cierra el objeto).
    img = Image.open(__import__("io").BytesIO(data))
    width, height = img.size
    if width < MIN_DIMENSION or height < MIN_DIMENSION:
        raise ValueError(f"Imagen demasiado pequeña ({width}x{height})")
    if width > MAX_DIMENSION or height > MAX_DIMENSION:
        raise ValueError(f"Imagen demasiado grande ({width}x{height})")

    fmt = (img.format or "").lower()
    mime = {"jpeg": "image/jpeg", "jpg": "image/jpeg", "png": "image/png", "webp": "image/webp"}.get(fmt)
    if mime not in ALLOWED_MIME:
        raise ValueError(f"Formato no soportado: {fmt or 'desconocido'}")
    return data, mime, width, height


def upload_images(request):
    """POST /agronomia-api/api/images.php — sube 1-5 fotos y las registra."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    photos = request.FILES.getlist("photos[]")
    if not photos:
        return _error(400, "Falta el campo photos[].")
    if len(photos) > MAX_IMAGES:
        return _error(422, f"Máximo {MAX_IMAGES} fotos por lote.")

    batch_id = str(uuid.uuid4())
    upload_dir = os.path.join(settings.PLANT_UPLOAD_DIR, batch_id)
    os.makedirs(upload_dir, exist_ok=True)

    saved = []
    for uploaded in photos:
        try:
            data, mime, width, height = _validate_image(uploaded)
        except ValueError as exc:
            return _error(422, str(exc))

        image_id = str(uuid.uuid4())
        ext = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}[mime]
        relative_path = os.path.join(batch_id, f"{image_id}.{ext}")
        with open(os.path.join(settings.PLANT_UPLOAD_DIR, relative_path), "wb") as fh:
            fh.write(data)

        PlantImage.objects.create(
            id=image_id, batch_id=batch_id, relative_path=relative_path,
            mime_type=mime, file_size=len(data), width=width, height=height,
            processing_status="pending",
        )
        saved.append({"id": image_id, "path": relative_path, "mime_type": mime, "size": len(data)})

    return JsonResponse({"success": True, "batch_id": batch_id, "images": saved}, status=201)


def identify(request):
    """POST /agronomia-api/api/identify — ejecuta el pipeline con skills."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    batch_id = (request.POST.get("batch_id") or "").strip()
    organ = (request.POST.get("organ") or "auto").strip()
    if not batch_id:
        return _error(400, "Falta batch_id.")

    images = list(PlantImage.objects.filter(batch_id=batch_id).order_by("created_at"))
    if not images:
        return _error(404, "No se encontraron imágenes para ese batch_id.")

    blobs = []
    for img in images:
        path = os.path.join(settings.PLANT_UPLOAD_DIR, img.relative_path)
        try:
            with open(path, "rb") as fh:
                blobs.append(fh.read())
        except OSError:
            return _error(500, "No se pudo leer una imagen almacenada.")

    result = pipeline.identify(blobs, organ=organ)

    # Guardar la corrida (permite resolver luego la candidata elegida sin
    # volver a llamar a Pl@ntNet).
    IdentificationRun.objects.create(
        batch_id=batch_id,
        organ=organ,
        status=result["status"],
        raw_json=result.get("raw") or {},
    )

    if result["status"] == "failed":
        return _error(502, result["message"])

    response = {"success": True, "status": result["status"]}
    if result["status"] == "identified":
        plant = result["plant"]
        PlantIdentification.objects.create(
            batch_id=batch_id,
            scientific_name=plant["scientific_name"],
            common_name=plant.get("common_name", ""),
            family=plant.get("family", ""),
            confidence=plant["confidence"],
            result_json=plant,
        )
        response["plant"] = plant
    elif result["status"] == "ambiguous":
        response["candidates"] = result["candidates"]
    else:
        response["message"] = result["message"]
        if result["status"] == "insufficient":
            response["plant_name"] = result.get("plant_name", "")
    # Las candidatas también acompañan a una identificación validada (útil para
    # el historial y para el panel web).
    if result.get("candidates") and "candidates" not in response:
        response["candidates"] = result["candidates"]
    return JsonResponse(response)


def confirm(request):
    """POST /agronomia-api/api/confirm — ficha de la candidata elegida."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    batch_id = (request.POST.get("batch_id") or "").strip()
    scientific_name = (request.POST.get("scientific_name") or "").strip()
    if not batch_id or not scientific_name:
        return _error(400, "Faltan batch_id o scientific_name.")

    run = IdentificationRun.objects.filter(batch_id=batch_id).order_by("-created_at").first()
    if run is None:
        return _error(404, "No hay una identificación previa para ese batch_id.")

    try:
        plant = pipeline.confirm(run.raw_json, scientific_name)
    except pipeline.InsufficientInfoError as exc:
        # La especie existe en la corrida, pero la IA no tiene sus cuidados.
        return JsonResponse({
            "success": True,
            "status": "insufficient",
            "message": str(exc),
            "plant_name": scientific_name,
        })
    except gemini.GeminiError as exc:
        return _error(502, str(exc))
    if plant is None:
        return _error(404, "La especie elegida no está entre las candidatas de ese lote.")

    PlantIdentification.objects.create(
        batch_id=batch_id,
        scientific_name=plant["scientific_name"],
        common_name=plant.get("common_name", ""),
        family=plant.get("family", ""),
        confidence=plant["confidence"],
        result_json=plant,
    )
    return JsonResponse({"success": True, "status": "identified", "plant": plant})


def chat(request):
    """POST /agronomia-api/api/chat — chat post-detección."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    message = (request.POST.get("message") or "").strip()
    session_id = (request.POST.get("session_id") or "default").strip()
    if not message:
        return _error(400, "Falta message.")

    # Contexto: el que envíe la app (planta detectada) o, si no, la última
    # identificación guardada.
    context_param = (request.POST.get("context") or "").strip()
    if context_param:
        context = context_param
    else:
        last = PlantIdentification.objects.order_by("-created_at").first()
        if last:
            plant = last.result_json
            context = (
                f"Nombre científico: {plant.get('scientific_name', '')}\n"
                f"Nombre común: {plant.get('common_name', '')}\n"
                f"Familia: {plant.get('family', '')}\n"
                f"Similitud: {int(plant.get('confidence', 0) * 100)}%\n"
            )
        else:
            context = "Aún no se ha detectado ninguna planta. Sugiere usar el escáner."

    history = [
        (m.role, m.content)
        for m in ChatMessage.objects.filter(session_id=session_id).order_by("created_at")
    ]
    try:
        reply = chat_service.reply(message, context, history)
    except Exception as exc:  # noqa: BLE001
        return _error(502, f"Error de la IA: {exc}")

    ChatMessage.objects.create(session_id=session_id, role="user", content=message)
    ChatMessage.objects.create(session_id=session_id, role="assistant", content=reply)
    return JsonResponse({"success": True, "reply": reply})

"""Vistas del backend Django (servicio de usuario).

Endpoints:
  POST /agronomia-api/api/images.php  → subir 1-5 fotos (contrato de Fernando)
  POST /agronomia-api/api/identify    → pipeline Pl@ntNet + Gemini (con skills)
  POST /agronomia-api/api/chat        → chat post-detección
"""
import os
import uuid

from django.conf import settings
from django.http import JsonResponse
from PIL import Image, UnidentifiedImageError

from core.models import ChatMessage, PlantIdentification, PlantImage
from core.services import chat as chat_service
from core.services import pipeline

# Formatos aceptados (por contenido, no por extensión).
ALLOWED_MIME = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGES = settings.MAX_UPLOAD_IMAGES
MAX_BYTES = settings.MAX_IMAGE_BYTES
MIN_DIMENSION = 16
MAX_DIMENSION = 4096


def _error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


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
    return JsonResponse(response)


def chat(request):
    """POST /agronomia-api/api/chat — chat post-detección."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    message = (request.POST.get("message") or "").strip()
    session_id = (request.POST.get("session_id") or "default").strip()
    if not message:
        return _error(400, "Falta message.")

    # Contexto: última identificación (si existe) para dar continuidad.
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
        context = "Aún no se ha detectado ninguna planta. Sugeriere usar el escáner."

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

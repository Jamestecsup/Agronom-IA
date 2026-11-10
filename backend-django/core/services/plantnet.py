"""Cliente de Pl@ntNet: identifica la especie por imagen."""
from io import BytesIO

import requests
from PIL import Image


class PlantNetError(RuntimeError):
    """Error al consultar Pl@ntNet."""


def identify(images, organ="auto", project="all", lang="es", api_key="", base_url="https://my-api.plantnet.org/", nb_results=10):
    """Llama a Pl@ntNet y devuelve la respuesta JSON.

    @param images: lista de bytes (JPEG o PNG).
    @param organ: órgano por foto ('auto', 'flower', 'leaf', 'fruit', 'bark').
    """
    if not api_key:
        raise PlantNetError("PLANTNET_API_KEY no configurada.")

    files = []
    for i, data in enumerate(images):
        try:
            with Image.open(BytesIO(data)) as image:
                image_type = (image.format or "").upper()
        except Exception as exc:  # noqa: BLE001 - solo se aceptan imágenes válidas
            raise PlantNetError("La foto no tiene un formato válido.") from exc
        file_info = {
            "JPEG": ("jpg", "image/jpeg"),
            "PNG": ("png", "image/png"),
        }.get(image_type)
        if file_info is None:
            raise PlantNetError("La foto debe estar en formato JPG o PNG.")
        extension, mime_type = file_info
        files.append(("images", (f"plant{i}.{extension}", data, mime_type)))
    # Pl@ntNet espera una parte 'organs' por cada imagen; los value se ignoran
    # salvo el nombre del form, así que enviamos el órgano como valor.
    for i in range(len(images)):
        files.append(("organs", (None, organ)))

    url = f"{base_url.rstrip('/')}/v2/identify/{project}"
    params = {"api-key": api_key, "lang": lang, "nb-results": nb_results}
    resp = requests.post(url, files=files, params=params, timeout=90)
    if resp.status_code == 404:
        # Pl@ntNet responde 404 "Species not found" cuando no reconoce ninguna
        # planta: es el caso "sin detección", no un fallo del servicio.
        return {"results": []}
    if resp.status_code != 200:
        raise PlantNetError(f"Pl@ntNet HTTP {resp.status_code}: {resp.text[:200]}")
    return resp.json()

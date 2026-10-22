"""Cliente de Pl@ntNet: identifica la especie por imagen."""
import requests


class PlantNetError(RuntimeError):
    """Error al consultar Pl@ntNet."""


def identify(images, organ="auto", project="all", lang="es", api_key="", base_url="https://my-api.plantnet.org/", nb_results=10):
    """Llama a Pl@ntNet y devuelve la respuesta JSON.

    @param images: lista de bytes (JPEG/PNG/WebP).
    @param organ: órgano por foto ('auto', 'flower', 'leaf', 'fruit', 'bark').
    """
    if not api_key:
        raise PlantNetError("PLANTNET_API_KEY no configurada.")

    files = [("images", (f"plant{i}.jpg", data, "image/jpeg")) for i, data in enumerate(images)]
    # Pl@ntNet espera una parte 'organs' por cada imagen; los value se ignoran
    # salvo el nombre del form, así que enviamos el órgano como valor.
    for i in range(len(images)):
        files.append(("organs", (None, organ)))

    url = f"{base_url.rstrip('/')}/v2/identify/{project}"
    params = {"api-key": api_key, "lang": lang, "nb-results": nb_results}
    resp = requests.post(url, files=files, params=params, timeout=90)
    if resp.status_code != 200:
        raise PlantNetError(f"Pl@ntNet HTTP {resp.status_code}: {resp.text[:200]}")
    return resp.json()

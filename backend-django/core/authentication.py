"""Tokens breves para asociar las peticiones de usuario a cuentas Django."""
from django.contrib.auth import get_user_model
from django.core import signing

TOKEN_SALT = "agronomia.user-api"
TOKEN_MAX_AGE = 60 * 60 * 24 * 14


def issue_user_token(user):
    """Crea un token firmado de 14 días; no requiere una tabla de sesiones."""
    return signing.dumps({"user_id": user.pk}, salt=TOKEN_SALT, compress=True)


def user_from_request(request):
    """Devuelve el usuario activo del bearer token o None si no es válido."""
    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        return None
    try:
        payload = signing.loads(
            token.strip(), salt=TOKEN_SALT, max_age=TOKEN_MAX_AGE
        )
        user_id = payload.get("user_id")
    except (signing.BadSignature, signing.SignatureExpired, AttributeError):
        return None
    if not user_id:
        return None
    User = get_user_model()
    return User.objects.filter(pk=user_id, is_active=True).first()

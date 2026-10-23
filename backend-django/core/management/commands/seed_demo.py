"""Crea los usuarios de demostración (administrador y agricultor).

Es idempotente: si el usuario ya existe solo actualiza su rol. Las contraseñas
se pueden cambiar con DEMO_ADMIN_PASSWORD / DEMO_USER_PASSWORD y son solo para
desarrollo local (nunca para producción).
"""
import os

from django.contrib.auth.models import User
from django.core.management.base import BaseCommand

from core.models import Profile


class Command(BaseCommand):
    help = "Crea o actualiza los usuarios demo: admin y agricultor."

    def add_arguments(self, parser):
        parser.add_argument(
            "--reset-passwords",
            action="store_true",
            help="Vuelve a poner la contraseña demo aunque el usuario ya exista.",
        )

    def handle(self, *args, **options):
        demo = [
            (
                "admin",
                os.environ.get("DEMO_ADMIN_PASSWORD", "Admin12345"),
                Profile.Role.ADMIN,
                "admin@agronomia.local",
            ),
            (
                "agricultor",
                os.environ.get("DEMO_USER_PASSWORD", "Campo12345"),
                Profile.Role.USER,
                "agricultor@agronomia.local",
            ),
        ]
        for username, password, role, email in demo:
            user, created = User.objects.get_or_create(
                username=username, defaults={"email": email}
            )
            if created or options["reset_passwords"]:
                user.set_password(password)
            user.email = user.email or email
            user.is_staff = role == Profile.Role.ADMIN
            user.save()
            Profile.objects.update_or_create(user=user, defaults={"role": role})
            self.stdout.write(
                self.style.SUCCESS(f"{username} ({role}) listo")
            )

import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    """Tablas nuevas del backend Django.

    No incluye `plant_images` (managed=False): esa tabla la creó el backend de
    Fernando y Django no la altera.
    """

    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="PlantIdentification",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("batch_id", models.CharField(db_index=True, max_length=36)),
                ("scientific_name", models.CharField(db_index=True, max_length=255)),
                ("common_name", models.CharField(blank=True, max_length=255)),
                ("family", models.CharField(blank=True, max_length=255)),
                ("confidence", models.FloatField()),
                ("result_json", models.JSONField()),
                ("created_at", models.DateTimeField(auto_now_add=True)),
            ],
        ),
        migrations.CreateModel(
            name="ChatMessage",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("session_id", models.CharField(db_index=True, max_length=64)),
                ("role", models.CharField(max_length=16)),
                ("content", models.TextField()),
                ("created_at", models.DateTimeField(auto_now_add=True)),
            ],
        ),
        migrations.CreateModel(
            name="Profile",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("role", models.CharField(choices=[("user", "Usuario"), ("admin", "Administrador")], default="user", max_length=16)),
                ("user", models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name="profile", to=settings.AUTH_USER_MODEL)),
            ],
        ),
    ]

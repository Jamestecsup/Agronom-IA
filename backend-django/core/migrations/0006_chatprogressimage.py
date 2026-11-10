import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0005_link_records_to_users"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="ChatProgressImage",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("relative_path", models.CharField(max_length=512, unique=True)),
                ("mime_type", models.CharField(max_length=32)),
                ("file_size", models.BigIntegerField()),
                ("caption", models.TextField(blank=True)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("message", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="progress_images", to="core.chatmessage")),
                ("plant", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="progress_images", to="core.plantidentification")),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="plant_progress_images", to=settings.AUTH_USER_MODEL)),
            ],
            options={"ordering": ("created_at",)},
        ),
    ]

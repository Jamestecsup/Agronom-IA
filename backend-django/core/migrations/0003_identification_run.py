import uuid

from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0002_plant_images_sqlite"),
    ]

    operations = [
        migrations.CreateModel(
            name="IdentificationRun",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("batch_id", models.CharField(db_index=True, max_length=36)),
                ("organ", models.CharField(default="auto", max_length=16)),
                ("status", models.CharField(max_length=16)),
                ("raw_json", models.JSONField(default=dict)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
            ],
        ),
    ]

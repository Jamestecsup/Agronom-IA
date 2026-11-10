from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


def add_plant_image_user_column(apps, schema_editor):
    """La tabla de imágenes es heredada y no la gestiona Django."""
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        if vendor == "mysql":
            cursor.execute(
                "ALTER TABLE plant_images ADD COLUMN user_id INT NULL, "
                "ADD INDEX idx_plant_images_user (user_id), "
                "ADD CONSTRAINT fk_plant_images_user FOREIGN KEY (user_id) "
                "REFERENCES auth_user (id) ON DELETE SET NULL"
            )
        elif vendor == "sqlite":
            cursor.execute(
                "ALTER TABLE plant_images ADD COLUMN user_id INTEGER NULL "
                "REFERENCES auth_user (id) ON DELETE SET NULL"
            )
            cursor.execute(
                "CREATE INDEX IF NOT EXISTS idx_plant_images_user "
                "ON plant_images (user_id)"
            )


def remove_plant_image_user_column(apps, schema_editor):
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        if vendor == "mysql":
            cursor.execute(
                "ALTER TABLE plant_images DROP FOREIGN KEY fk_plant_images_user, "
                "DROP INDEX idx_plant_images_user, DROP COLUMN user_id"
            )
        elif vendor == "sqlite":
            cursor.execute("DROP INDEX IF EXISTS idx_plant_images_user")
            cursor.execute("ALTER TABLE plant_images DROP COLUMN user_id")


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0004_plantimage"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AddField(
            model_name="plantidentification",
            name="user",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="plant_identifications",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="identificationrun",
            name="user",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="identification_runs",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="chatmessage",
            name="user",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="chat_messages",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.RunPython(add_plant_image_user_column, remove_plant_image_user_column),
    ]

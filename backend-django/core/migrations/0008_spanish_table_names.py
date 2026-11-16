from django.db import migrations


def rename_plant_images(apps, schema_editor):
    """Renombra la tabla heredada que Django no administra directamente."""
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        if vendor == "mysql":
            cursor.execute("RENAME TABLE plant_images TO imagenes_planta")
        elif vendor == "sqlite":
            cursor.execute("ALTER TABLE plant_images RENAME TO imagenes_planta")


def restore_plant_images(apps, schema_editor):
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        if vendor == "mysql":
            cursor.execute("RENAME TABLE imagenes_planta TO plant_images")
        elif vendor == "sqlite":
            cursor.execute("ALTER TABLE imagenes_planta RENAME TO plant_images")


class Migration(migrations.Migration):
    dependencies = [("core", "0007_weekly_schedule_and_care_plan")]

    operations = [
        # PlantImage es unmanaged; se actualiza el estado de Django y se
        # renombra explícitamente para no dejar atrás la tabla heredada.
        migrations.AlterModelTable(name="plantimage", table="imagenes_planta"),
        migrations.RunPython(rename_plant_images, restore_plant_images),
        migrations.AlterModelTable(name="plantidentification", table="identificaciones_planta"),
        migrations.AlterModelTable(name="identificationrun", table="procesos_identificacion"),
        migrations.AlterModelTable(name="chatmessage", table="mensajes_chat"),
        migrations.AlterModelTable(name="chatprogressimage", table="imagenes_progreso_chat"),
        migrations.AlterModelTable(name="pendingscheduleimport", table="importaciones_horario_pendientes"),
        migrations.AlterModelTable(name="weeklyschedule", table="horarios_semanales"),
        migrations.AlterModelTable(name="careplantask", table="tareas_cuidado"),
        migrations.AlterModelTable(name="profile", table="perfiles_usuario"),
    ]

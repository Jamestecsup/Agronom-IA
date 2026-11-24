from django.db import migrations


RENAMES = [
    ("imagenes_planta", "plant_images"),
    ("identificaciones_planta", "plant_identifications"),
    ("procesos_identificacion", "identification_runs"),
    ("mensajes_chat", "chat_messages"),
    ("imagenes_progreso_chat", "chat_progress_images"),
    ("importaciones_horario_pendientes", "pending_schedule_imports"),
    ("horarios_semanales", "weekly_schedules"),
    ("tareas_cuidado", "care_plan_tasks"),
    ("perfiles_usuario", "user_profiles"),
]


def _table_exists(cursor, vendor, table):
    if vendor == "mysql":
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = %s",
            [table],
        )
        return cursor.fetchone()[0] > 0
    if vendor == "sqlite":
        cursor.execute(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=%s",
            [table],
        )
        return cursor.fetchone()[0] > 0
    return False


def rename_to_english(apps, schema_editor):
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        for old, new in RENAMES:
            if _table_exists(cursor, vendor, old) and not _table_exists(cursor, vendor, new):
                if vendor == "mysql":
                    cursor.execute(f"RENAME TABLE `{old}` TO `{new}`")
                elif vendor == "sqlite":
                    cursor.execute(f'ALTER TABLE "{old}" RENAME TO "{new}"')


def rename_to_spanish(apps, schema_editor):
    vendor = schema_editor.connection.vendor
    with schema_editor.connection.cursor() as cursor:
        for old, new in reversed(RENAMES):
            if _table_exists(cursor, vendor, new) and not _table_exists(cursor, vendor, old):
                if vendor == "mysql":
                    cursor.execute(f"RENAME TABLE `{new}` TO `{old}`")
                elif vendor == "sqlite":
                    cursor.execute(f'ALTER TABLE "{new}" RENAME TO "{old}"')


class Migration(migrations.Migration):

    dependencies = [
        ("core", "0009_alter_careplantask_options_alter_chatmessage_options_and_more"),
    ]

    operations = [
        migrations.SeparateDatabaseAndState(
            database_operations=[
                migrations.RunPython(rename_to_english, rename_to_spanish),
            ],
            state_operations=[
                migrations.AlterModelTable(name="plantimage", table="plant_images"),
                migrations.AlterModelTable(name="plantidentification", table="plant_identifications"),
                migrations.AlterModelTable(name="identificationrun", table="identification_runs"),
                migrations.AlterModelTable(name="chatmessage", table="chat_messages"),
                migrations.AlterModelTable(name="chatprogressimage", table="chat_progress_images"),
                migrations.AlterModelTable(name="pendingscheduleimport", table="pending_schedule_imports"),
                migrations.AlterModelTable(name="weeklyschedule", table="weekly_schedules"),
                migrations.AlterModelTable(name="careplantask", table="care_plan_tasks"),
                migrations.AlterModelTable(name="profile", table="user_profiles"),
            ],
        ),
    ]

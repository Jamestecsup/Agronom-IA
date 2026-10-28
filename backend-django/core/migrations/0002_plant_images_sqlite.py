from django.db import connection, migrations


CREATE_SQLITE = [
    """
    CREATE TABLE IF NOT EXISTS plant_images (
        id CHAR(36) NOT NULL PRIMARY KEY,
        batch_id CHAR(36) NOT NULL,
        relative_path VARCHAR(255) NOT NULL UNIQUE,
        mime_type VARCHAR(32) NOT NULL,
        file_size BIGINT NOT NULL,
        width INT NOT NULL,
        height INT NOT NULL,
        processing_status VARCHAR(16) NOT NULL DEFAULT 'pending',
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_plant_images_batch ON plant_images (batch_id)",
    "CREATE INDEX IF NOT EXISTS idx_plant_images_created ON plant_images (created_at)",
]


def create_plant_images_sqlite(apps, schema_editor):
    """Crea la tabla legada `plant_images` solo en SQLite.

    En MySQL esa tabla la creó el backend de Fernando y ya existe; Django no
    la altera (managed=False).
    """
    if connection.vendor != "sqlite":
        return
    with connection.cursor() as cursor:
        for statement in CREATE_SQLITE:
            cursor.execute(statement)


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0001_initial"),
    ]

    operations = [
        migrations.RunPython(create_plant_images_sqlite, migrations.RunPython.noop),
    ]

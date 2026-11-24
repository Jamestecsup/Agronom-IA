"""Entrega el dominio a Flyway sin modificar las tablas existentes."""

from django.db import migrations


class Migration(migrations.Migration):
    dependencies = [("core", "0009_alter_careplantask_options_alter_chatmessage_options_and_more")]

    operations = [
        migrations.AlterModelOptions(
            name='plantidentification',
            options={'managed': False, 'verbose_name': 'identificación de planta', 'verbose_name_plural': 'identificaciones de plantas'},
        ),
        migrations.AlterModelOptions(
            name='identificationrun',
            options={'managed': False, 'verbose_name': 'proceso de identificación', 'verbose_name_plural': 'procesos de identificación'},
        ),
        migrations.AlterModelOptions(
            name='chatmessage',
            options={'managed': False, 'verbose_name': 'mensaje de chat', 'verbose_name_plural': 'mensajes de chat'},
        ),
        migrations.AlterModelOptions(
            name='chatprogressimage',
            options={'managed': False, 'verbose_name': 'imagen de progreso del chat', 'verbose_name_plural': 'imágenes de progreso del chat', 'ordering': ('created_at',)},
        ),
        migrations.AlterModelOptions(
            name='pendingscheduleimport',
            options={'managed': False, 'verbose_name': 'importación pendiente de horario', 'verbose_name_plural': 'importaciones pendientes de horario'},
        ),
        migrations.AlterModelOptions(
            name='weeklyschedule',
            options={'managed': False, 'verbose_name': 'horario semanal', 'verbose_name_plural': 'horarios semanales'},
        ),
        migrations.AlterModelOptions(
            name='careplantask',
            options={'managed': False, 'verbose_name': 'tarea de cuidado', 'verbose_name_plural': 'tareas de cuidado', 'ordering': ('day_of_week', 'start_time')},
        ),
        migrations.AlterModelOptions(
            name='profile',
            options={'managed': False, 'verbose_name': 'perfil de usuario', 'verbose_name_plural': 'perfiles de usuario'},
        ),
    ]

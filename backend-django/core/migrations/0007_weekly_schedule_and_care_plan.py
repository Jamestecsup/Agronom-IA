import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0006_chatprogressimage"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="PendingScheduleImport",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("source_pdf", models.FileField(upload_to="schedules/pending/%Y/%m/")),
                ("parsed_classes", models.JSONField(default=list)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="pending_schedule_imports", to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name="WeeklySchedule",
            fields=[
                ("classes", models.JSONField(default=list)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("user", models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, primary_key=True, related_name="weekly_schedule", serialize=False, to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name="CarePlanTask",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("title", models.CharField(max_length=180)),
                ("instructions", models.TextField(blank=True)),
                ("day_of_week", models.PositiveSmallIntegerField()),
                ("start_time", models.TimeField()),
                ("duration_minutes", models.PositiveSmallIntegerField(default=20)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("plant", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="scheduled_care", to="core.plantidentification")),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="care_plan_tasks", to=settings.AUTH_USER_MODEL)),
            ],
            options={"ordering": ("day_of_week", "start_time")},
        ),
        migrations.AddIndex(
            model_name="careplantask",
            index=models.Index(fields=["user", "day_of_week", "start_time"], name="core_carepl_user_id_43a280_idx"),
        ),
    ]

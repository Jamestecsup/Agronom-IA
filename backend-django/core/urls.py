from django.urls import path

from core import auth_views, views

urlpatterns = [
    path("health", views.health),
    path("images.php", views.upload_images),
    path("identify", views.identify),
    path("confirm", views.confirm),
    path("plants", views.identified_plants),
    path("plants/<uuid:plant_id>/cover", views.plant_cover),
    path("chat", views.chat),
    path("chat/history", views.chat_history),
    path("chat/images", views.chat_progress_images),
    path("chat/images/<uuid:image_id>", views.chat_progress_image),
    path("schedule", views.weekly_schedule),
    path("schedule/import", views.schedule_import),
    path("schedule/import/<uuid:import_id>", views.schedule_import_action),
    path("schedule/care-plan/draft", views.care_plan_draft),
    path("schedule/care-plan/confirm", views.care_plan_confirm),
    path("schedule/care-plan/tasks/<uuid:task_id>", views.care_plan_task_action),
    path("register", auth_views.register),
    path("login", auth_views.login),
]

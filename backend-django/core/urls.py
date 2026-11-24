from django.urls import path
from django.views.decorators.csrf import csrf_exempt

from core import auth_views, views


def api_path(route, view):
    # Este URLconf solo se incluye bajo /agronomia-api/api/. Las rutas
    # protegidas leen Authorization: Bearer y no autentican por cookies.
    return path(route, csrf_exempt(view))


urlpatterns = [
    api_path("health", views.health),
    api_path("images.php", views.upload_images),
    api_path("identify", views.identify),
    api_path("confirm", views.confirm),
    api_path("plants", views.identified_plants),
    api_path("plants/<uuid:plant_id>/cover", views.plant_cover),
    api_path("chat", views.chat),
    api_path("chat/history", views.chat_history),
    api_path("chat/images", views.chat_progress_images),
    api_path("chat/images/<uuid:image_id>", views.chat_progress_image),
    api_path("schedule", views.weekly_schedule),
    api_path("schedule/import", views.schedule_import),
    api_path("schedule/import/<uuid:import_id>", views.schedule_import_action),
    api_path("schedule/care-plan/draft", views.care_plan_draft),
    api_path("schedule/care-plan/confirm", views.care_plan_confirm),
    api_path("schedule/care-plan/tasks/<uuid:task_id>", views.care_plan_task_action),
    api_path("register", auth_views.register),
    api_path("login", auth_views.login),
]

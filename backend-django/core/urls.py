from django.urls import path

from core import auth_views, views

urlpatterns = [
    path("images.php", views.upload_images),
    path("identify", views.identify),
    path("confirm", views.confirm),
    path("chat", views.chat),
    path("register", auth_views.register),
    path("login", auth_views.login),
]

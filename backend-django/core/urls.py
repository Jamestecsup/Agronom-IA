"""Endpoints de usuario desactivados en Django.

La inversión de backends dejó a Spring Boot (:8081) como dueño de los endpoints
de usuario/web y móvil. Django (:8000) conserva solo el panel admin (/api/admin/*).
"""

urlpatterns = []

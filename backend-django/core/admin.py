from django.contrib import admin

from core.models import (
    CarePlanTask,
    ChatMessage,
    ChatProgressImage,
    IdentificationRun,
    PendingScheduleImport,
    PlantIdentification,
    PlantImage,
    Profile,
    WeeklySchedule,
)

admin.site.site_header = "Administración Agronom-IA"
admin.site.site_title = "Agronom-IA | Django Admin"
admin.site.index_title = "Gestión de datos del proyecto"


class ChatProgressImageInline(admin.TabularInline):
    model = ChatProgressImage
    extra = 0
    can_delete = False
    fields = ("caption", "message", "relative_path", "created_at")
    readonly_fields = fields

    def has_add_permission(self, request, obj=None):
        return False


@admin.register(PlantIdentification)
class PlantIdentificationAdmin(admin.ModelAdmin):
    list_display = (
        "common_name",
        "scientific_name",
        "family",
        "confidence",
        "user",
        "created_at",
    )
    list_filter = ("family",)
    search_fields = (
        "common_name",
        "scientific_name",
        "family",
        "user__username",
        "batch_id",
    )
    list_select_related = ("user",)
    readonly_fields = ("id", "created_at")
    ordering = ("-created_at",)
    inlines = (ChatProgressImageInline,)


@admin.register(IdentificationRun)
class IdentificationRunAdmin(admin.ModelAdmin):
    list_display = ("batch_id", "organ", "status", "user", "created_at")
    list_filter = ("status", "organ")
    search_fields = ("batch_id", "user__username")
    list_select_related = ("user",)
    readonly_fields = ("id", "created_at", "raw_json")


@admin.register(ChatMessage)
class ChatMessageAdmin(admin.ModelAdmin):
    list_display = ("short_content", "role", "user", "session_id", "created_at")
    list_filter = ("role",)
    search_fields = ("content", "session_id", "user__username")
    list_select_related = ("user",)
    readonly_fields = ("id", "created_at")

    @admin.display(description="Mensaje")
    def short_content(self, obj):
        return obj.content[:100]


@admin.register(ChatProgressImage)
class ChatProgressImageAdmin(admin.ModelAdmin):
    list_display = ("plant", "user", "message", "mime_type", "file_size", "created_at")
    list_filter = ("mime_type",)
    search_fields = ("plant__scientific_name", "plant__common_name", "user__username", "caption")
    list_select_related = ("plant", "user", "message")
    readonly_fields = ("id", "created_at")


@admin.register(PlantImage)
class PlantImageAdmin(admin.ModelAdmin):
    list_display = ("relative_path", "batch_id", "user", "mime_type", "file_size", "created_at")
    list_filter = ("mime_type", "processing_status")
    search_fields = ("relative_path", "batch_id", "user__username")
    list_select_related = ("user",)
    readonly_fields = tuple(field.name for field in PlantImage._meta.fields)

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(Profile)
class ProfileAdmin(admin.ModelAdmin):
    list_display = ("user", "role")
    list_filter = ("role",)
    search_fields = ("user__username", "user__email")
    list_select_related = ("user",)
    autocomplete_fields = ("user",)


@admin.register(WeeklySchedule)
class WeeklyScheduleAdmin(admin.ModelAdmin):
    list_display = ("user", "updated_at")
    search_fields = ("user__username", "user__email")
    list_select_related = ("user",)
    readonly_fields = ("updated_at",)


@admin.register(PendingScheduleImport)
class PendingScheduleImportAdmin(admin.ModelAdmin):
    list_display = ("user", "created_at", "source_pdf")
    search_fields = ("user__username", "user__email")
    list_select_related = ("user",)
    readonly_fields = ("id", "created_at", "parsed_classes")


@admin.register(CarePlanTask)
class CarePlanTaskAdmin(admin.ModelAdmin):
    list_display = ("title", "plant", "user", "day_of_week", "start_time", "duration_minutes")
    list_filter = ("day_of_week",)
    search_fields = ("title", "instructions", "plant__common_name", "plant__scientific_name", "user__username")
    list_select_related = ("plant", "user")
    autocomplete_fields = ("plant", "user")
    readonly_fields = ("id", "created_at")

# Migración de backends (Opción A)

Actualizado 2026-11-24 · Rama local `feature/swap-backends`.

Destino acordado: Spring Boot sirve Android y la web de usuario. Django sirve la web de administración. Ambos usan MySQL Server 8.4 y comparten las cuentas del proyecto. El destino elimina el puente HTTP entre backends.

| Fase | Estado | Evidencia o siguiente hito |
|---|---|---|
| 0. Preparación | Completa | La rama parte de `config/mysql-server` (`1619e9b)) e integra `fix/api-csrf` (`8208c7c)) en `da16adf`. La matriz de rutas está en la nota de Obsidian «Migración de backends (Opción A)». Pasaron las dos pruebas de regresión CSRF con SQLite en memoria. |
| 1. Propiedad de datos | Completa | Django inicializa con 0001–0009 y 0010 modifica solo el estado. Spring valida el esquema y registra línea base 1. Verificado en MySQL 8.4.13 nuevo y existente. |
| 2. API de usuario en Spring | Implementada y verificada con dobles | Registro, Bearer, fotos, plantas, identificación, chat y PDF/cuidados. Los dobles HTTP verificaron confirmación sin volver a identificar y fotografías en su turno. |
| 3. API de administración en Django | Implementada y verificada | Ocho rutas del panel con Bearer firmado por Django. Se mantienen CSRF en el sitio nativo y los campos camelCase del panel React. |
| 4. Clientes y configuración | Implementada | Proxies web, URL base Android, variables y arranque local apuntan a los servicios correspondientes. |
| 5. Calidad de las fichas | Implementada y verificada con dobles | Instrucciones revisadas, ocho secciones con condicionante y aviso orientativo en web y Android. |
| 6. Identificación asíncrona | Diseño, implementación diferida | Mantener las rutas síncronas. Crear un trabajo autenticado que registre el dueño, el lote, el estado y el paso actual en MySQL, con idempotencia por solicitud y un ejecutor acotado. La web consultaría solo trabajos propios. Android seguiría con la ruta síncrona hasta probar una transición. |
| Verificación final | Ejecutada, con límite en la calidad observada | Pruebas Spring/Django, las dos webs y Android. Humo integral con dobles. Un escaneo real exitoso, cuatro de seis llamadas a IA, una sección propia y siete de respaldo. |

## Fase 1 · Propiedad del esquema

La instalación tiene un único orden. Primero se aprovisiona MySQL con el SQL del proyecto y se ejecuta `scripts/migrar-db.ps1`. Django aplica las migraciones históricas 0001–0009 y registra 0010 de solo estado. Spring comprueba que existan `auth_user` y las nueve tablas de dominio y registra la línea base Flyway 1. JPA valida las columnas mediante `ddl-auto=validate`. La línea base se documenta en Obsidian. Los cambios futuros del dominio van en Flyway desde V2 y se reflejan manualmente en el ORM de Django. El framework `auth_*` y `django_*` sigue bajo Django.

Flyway usa `agronomia_admin` y Spring usa `agronomia_api`. Las cuentas operativas comparten `auth_user` y `perfiles_usuario`. Se verificó con contraseñas sintéticas Unicode que ambos backends leen hashes escritos por el otro. El codificador de Spring valida y genera el formato `pbkdf2_sha256` que Django usa para esas contraseñas.

Se comprobó que Spring rechaza una base sin migrar antes de registrar Flyway, y que inicia con validación JPA después de Django tanto en una instancia MySQL 8.4.13 aislada como en la base actual. Las dos bases registraron 0010 y línea base 1. Las migraciones 0001–0009 se conservaron. La preparación y estas verificaciones no consumieron cuota externa.

## Fase 2 · Colisión en la ruta de carga

Django y Spring publican la misma ruta `POST /agronomia-api/api/images.php`. Django exige Bearer y acepta JPEG/PNG (`backend-django/core/views.py:32,441–468`). La versión heredada de Spring recibe la petición sin Bearer y una prueba exige WebP con HTTP 201 (`ImageUploadController.java:22–25` y `ImageUploadApiIntegrationTest.java:116–121`). La web de usuario filtra JPEG/PNG y Android convierte sus selecciones a JPEG antes de transmitirlas. El equipo aprobó retirar el contrato heredado. Se mantiene la ruta y el JSON de Django. Se exige Bearer a una cuenta activa y se guardan las fotos con su usuario. JPEG/PNG devuelven 201, WebP devuelve 422 y sin Bearer se devuelve 401. Las pruebas de integración heredadas se conservaron y adaptaron.

## Decisiones de paridad

- Reutilizados `ImageBatchService`, el guardado transaccional de metadatos y la validación por firma y decodificación. El contrato Django gana sobre el WebP público anterior.
- Se reutiliza PDFBox para extraer el horario. El cliente IA central devuelve 502 con mensaje explícito cuando el proveedor rechaza imágenes. El puente antiguo se retiró.
- El panel vuelve a validar rol y cuenta activa en cada petición. Un cambio de estado invalida el acceso aunque el token siga dentro de sus ocho horas. Los clientes deben iniciar sesión otra vez al cambiar de backend.
- El ciclo de registro usa el mismo PBKDF2 en ambos backends. Django aplica CSRF al sitio nativo y exime solo la API del panel. Spring usa Bearer explícito sin sesiones ni autenticación de cookie.
- Web de usuario y Android muestran el aviso de contenido orientativo. Las fichas usan condiciones por categoría y un texto prudente de respaldo. Se mantuvieron todos los campos de la ficha.
- Limitación operacional: los contadores de login son locales por proceso. Para varias réplicas, configurar un límite compartido y un almacenamiento compartido privado para las imágenes; Django solo necesita lectura del volumen.


## Evidencia de verificación 2026-11-24

- Pasaron 19 pruebas de Spring y cuatro de Django. Las dos webs compilaron con Vite y Android compiló con `assembleDebug`.
- El humo en una instancia descartable de MySQL, sin proveedores externos, cubrió registro, login, carga, identificación ambigua, confirmación, portada, chat con foto, historial, PDF, alta y reprogramación de cuidados, panel y revocación de sesión. Confirmar no volvió a llamar a Pl@ntNet. Una prueba local reproduce una respuesta con cabeceras prontas y cuerpo lento para verificar el plazo de IA.
- El único escaneo real, desde Spring, identificó `Helianthus annuus` con 44,031 % como candidata. La confirmación HTTP 200 produjo ocho secciones, una con texto propio y siete de respaldo. Usó cuatro de seis llamadas de IA, sin 429. El recorrido duró 169,50 s.
- El humo detectó que el cuerpo de una respuesta de OpenRouter tardó 122,193 s. Se aplicó un tope de 90 s a la recepción completa y se explicitó la forma de ocho claves obligatorias en el prompt de categorías. Se volvió a ejecutar el humo con dobles sobre la versión final. No se repitió el escaneo real. Queda por validar con otra prueba autorizada si el modelo configurado devuelve las ocho categorías con contenido propio.

## Diseño diferido de la Fase 6

Propuesta: añadir `POST /agronomia-api/api/identification-jobs` y `GET /agronomia-api/api/identification-jobs/{id}`, manteniendo los síncronos. Una migración Flyway V2 o posterior guardaría usuario, lote, estado y paso, tiempos, resultado y una clave de idempotencia por usuario. Un ejecutor acotado reclama trabajos pendientes, limita concurrencia y marca fallos recuperables. La web consultaría el estado con espera creciente y mostraría candidatas o ficha. Android conservaría el flujo síncrono hasta probar la transición.

## Correcciones de ficha, tiempos y seguridad · 2026-11-24

- Categorías: INFO registra longitud y claves faltantes, desconocidas y no textuales. La vista previa de hasta 300 caracteres solo existe con `AI_DEBUG_RESPONSES` activo, desactivado en la plantilla. Se normalizan claves en español y mayúsculas y se aplanan listas y objetos. Con cuatro o más categorías vacías se pide una sola corrección. Si sigue incompleta, se devuelve `insufficient`.
- Plazos: 20 s para pistas sin reintento, 25 s para refinamiento, 90 s para categorías y 30 s para glosario y materiales en paralelo. Presupuesto común de 150 s para identificar o confirmar. Spring descarta esperas por cuota que exceden el tiempo disponible.
- Seguridad: Django exige una clave o un permiso explícito de desarrollo. Spring verifica Bearer antes de resolver multipart, limita tamaño, texto y registros por IP, exige contraseñas de ocho caracteres y registra fecha UTC.
- Verificación local: 33 pruebas Spring y 4 Django, más compilación de las dos webs. Un servidor lento envió las cabeceras sin terminar el cuerpo. `confirm` devolvió HTTP 502 en una prueba de 90,152 s, dentro de los 180 s requeridos. También se verificó el fallo de arranque de Django sin clave y el arranque con permiso explícito de desarrollo.
- Se completaron las notas «Configuración de IA» y «HU-07». Se hicieron cero escaneos reales y cero llamadas a proveedores externos. Falta el escaneo real que se autorice por separado.

## Glosario, materiales y seguridad de las fichas · 2026-11-24

- Verificado en `51f0477`. Las skills pedían `terms` o `items` en la raíz y Spring buscaba listas por categoría. Sigue el formato por lotes de `glossary_batch` y `materials_batch` del Django original. El prompt incluye las claves de cada solicitud y los campos `word`/`meaning` o `name`/`detail`. El formato plano solo se recupera cuando se solicitó una categoría.
- Glosario y materiales conservan la ejecución en paralelo. Cada paso registra grupos ausentes o inválidos y el número de elementos aceptados y descartados, sin registrar su contenido.
- Las candidatas ambiguas mantienen `hint: ""` y `terms: []` por defecto, sin consultar la IA. `AI_CANDIDATE_HINTS=true` habilita la generación anterior. Web y Android siguen mostrando las fotos de referencia de Wikimedia.
- Todas las secciones de las fichas nuevas pasan por un filtro de oraciones. Las secciones afectadas incluyen el aviso de consulta antes de consumir la planta o usarla con fines medicinales. El registro informa el número de oraciones retiradas. `AI_SENSITIVE_TERMS` añade palabras o frases, separadas por comas, a la lista integrada. El filtro reconoce texto por términos y puede omitir paráfrasis. Las skills piden español común y restringen las afirmaciones y los usos a lo pertinente a la especie.
- Pasaron 39 pruebas de Spring y 4 de Django. Los dobles cubren listas por lote, formato plano, listas vacías, campos incompletos y afirmaciones sensibles. La prueba HTTP del resultado ambiguo tardó 0,646 s y verificó cero llamadas a la IA. Esta ronda no incluye escaneos reales ni llamadas a proveedores externos.

## Plazo y modelo auxiliar del enriquecimiento · 2026-11-24

- En el escaneo autorizado de `8be5146`, glosario y materiales agotaron sus 30 s. La respuesta por lotes quedó sin verificar. Ahora ambos pasos corren en paralelo con hasta 75 s cada uno, sujetos al presupuesto global de 150 s. Se omiten si quedan menos de 5 s. El log INFO registra el tiempo restante y el plazo efectivo al iniciar cada paso opcional.
- Las skills piden de 3 a 5 términos de glosario o de 3 a 4 materiales por categoría cuando hay elementos pertinentes y una frase breve por elemento. Spring aplica máximos de 5 y 4. El log distingue el formato por lotes, plano, inesperado y sin respuesta, y registra elementos aceptados y descartados.
- `AI_AUX_MODEL` es opcional y solo selecciona el modelo de glosario y materiales. Si se omite, se usa `AI_MODEL`. La plantilla incluye como ejemplo `nvidia/nemotron-3.5-lightning:free`.
- `LiveEnrichmentTest` se habilita solo con `LIVE_AI=true`. Usa texto fijo para ocho categorías, omite la identificación y limita la ejecución a dos llamadas en paralelo, sin reintentos. El diagnóstico en vivo queda pendiente de autorización.
- Pasaron 43 pruebas de Spring y 4 de Django. La suite offline omitió la prueba en vivo. Los dobles verificaron la ejecución paralela, los plazos de 75 s y el presupuesto restante, los límites de elementos y la selección del modelo.

## Auditoría y diseño de modelos por usuario · 2026-11-25

- Rama local `feature/ia-modelos-y-web` creada desde `54deab1`. La nota Obsidian «Modelos de IA por usuario» recoge la evidencia de la web, las amenazas y la dirección Orgánica Biofílica seleccionada con UI/UX Pro Max.
- Base Vite de 42 módulos, JS de entrada de 173,42 kB (56,12 gzip) y CSS de 17,06 kB (4,40 gzip). El jardín tenía un límite local de 30 y las pantallas se montaban sin rutas.
- Las credenciales se vinculan al usuario y al perfil mediante AES-GCM. El transporte resuelve y fija la dirección permitida antes de cada conexión, con redirecciones desactivadas. `AI_ALLOW_PRIVATE_HOSTS` habilita explícitamente HTTP solo para direcciones de desarrollo, con metadatos, CGNAT y link-local bloqueados.
- Se conserva la compatibilidad de Android y Django mediante contratos aditivos de Spring. Las migraciones se validan en una base descartable.

# Pruebas y calidad

## Django (`backend-django`)

- **Unitarias**: `core/tests/unit/` (`SimpleTestCase`, mocks; sin red/BD externa).
- **Integración**: `core/tests/integration/` (`TestCase`/cliente Django, BD de test).
- **Estáticas**: `ruff` (config en `pyproject.toml`).

Comandos:

```powershell
cd backend-django
py -m pip install -r requirements-dev.txt
py -m ruff check .
py manage.py test core.tests.unit -v 2
py manage.py test core.tests.integration -v 2
py manage.py test core.tests -v 2
```

## Spring Boot (`backend-spring`)

- **Unitarias**: `*Test.java` (JUnit/Mockito, sin `@SpringBootTest` cuando sea posible).
- **Integración**: `*IntegrationTest.java` / `*IT.java` (MockMvc, H2 modo MySQL, Failsafe).
- **Cobertura**: JaCoCo report en `target/site/jacoco/index.html` tras `verify`.

Comandos:

```powershell
cd backend-spring
.\mvnw.cmd test
.\mvnw.cmd verify
```

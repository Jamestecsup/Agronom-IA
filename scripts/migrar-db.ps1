# Aplica migraciones con la cuenta de mantenimiento, solo en el proceso hijo.
$ErrorActionPreference = "Stop"
$django = Join-Path (Split-Path -Parent $PSScriptRoot) "backend-django"
$python = Join-Path $django ".venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $python)) { throw "Falta backend-django/.venv. Instala primero los requisitos de Django." }
if (-not (Test-Path -LiteralPath (Join-Path $django ".env"))) { throw "Falta backend-django/.env." }

$programa = @'
import os
from pathlib import Path
import subprocess
import sys
from dotenv import dotenv_values

django = Path(sys.argv[1])
config = dotenv_values(django / ".env", interpolate=False)
required = ("DB_NAME", "DB_HOST", "DB_PORT", "DB_MIGRATE_USER", "DB_MIGRATE_PASSWORD")
missing = [name for name in required if not (config.get(name) or "").strip()]
if missing:
    sys.exit("Faltan variables en backend-django/.env: " + ", ".join(missing))
if config["DB_MIGRATE_USER"] != "agronomia_admin":
    sys.exit("DB_MIGRATE_USER debe ser la cuenta agronomia_admin.")
env = os.environ.copy()
env.update({key: config[key] for key in ("DB_NAME", "DB_HOST", "DB_PORT")})
env.update(DB_USER=config["DB_MIGRATE_USER"], DB_PASSWORD=config["DB_MIGRATE_PASSWORD"], USE_SQLITE="0")
sys.exit(subprocess.call([sys.executable, "manage.py", "migrate"], cwd=django, env=env))
'@
$programa | & $python - $django
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
# Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
#
# Publica una versión nueva de la app Android (sin Google Play):
#   1. Compila la app firmada con la firma oficial (Documentos\MesonAsiste-Firma).
#   2. La sube a GitHub como "release" (descarga pública del archivo .apk).
#   3. Actualiza web/version.json; al subirlo, todas las apps instaladas muestran el aviso "Nueva versión".
#
# Antes de ejecutarlo, sube versionCode y versionName en app/build.gradle.kts.
# Uso:  .\publicar-version.ps1 -Notas "Qué cambió en esta versión"
param([Parameter(Mandatory = $true)][string]$Notas)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path "$env:USERPROFILE\Documents\MesonAsiste-Firma\firma.properties")) {
  throw 'No se encontró la firma oficial en Documentos\MesonAsiste-Firma. Sin ella no se pueden publicar actualizaciones.'
}
$gradle = Get-Content 'app\build.gradle.kts' -Raw
$codigo = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$nombre = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$etiqueta = "v$nombre"
gh release view $etiqueta *> $null
if ($LASTEXITCODE -eq 0) { throw "La versión $nombre ya fue publicada. Sube versionCode y versionName en app/build.gradle.kts." }

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
& .\gradlew.bat :app:assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar la app.' }

$apk = Join-Path $env:TEMP "MesonAsiste-$nombre.apk"
Copy-Item 'app\build\outputs\apk\release\app-release.apk' $apk -Force
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()

gh release create $etiqueta $apk --title "Mesón Asiste $nombre" --notes $Notas
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la versión en GitHub.' }

$version = [ordered]@{
  versionCode = $codigo
  versionName = $nombre
  apk         = "https://github.com/wyecg28-ops/meson-asiste/releases/download/$etiqueta/MesonAsiste-$nombre.apk"
  sha256      = $sha
  notas       = $Notas
}
[IO.File]::WriteAllText("$PSScriptRoot\web\version.json", ($version | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))

git add web/version.json app/build.gradle.kts
git commit -q -m "Publicar Mesón Asiste $nombre"
git push -q origin HEAD
git push -q origin HEAD:main
Write-Host "Listo: Mesón Asiste $nombre publicada. Las apps instaladas mostrarán el aviso en unos minutos."

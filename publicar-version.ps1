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
cmd /c "gh release view $etiqueta >nul 2>&1"
if ($LASTEXITCODE -eq 0) { throw "La versión $nombre ya fue publicada. Sube versionCode y versionName en app/build.gradle.kts." }

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
& .\gradlew.bat :app:assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar la app.' }

$apk = Join-Path $env:TEMP "MesonAsiste-$nombre.apk"
Copy-Item 'app\build\outputs\apk\release\app-release.apk' $apk -Force
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
# Copia con nombre fijo: el enlace .../releases/latest/download/MesonAsiste.apk siempre baja la última versión.
$apkFijo = Join-Path $env:TEMP 'MesonAsiste.apk'
Copy-Item $apk $apkFijo -Force

gh release create $etiqueta $apk $apkFijo --title "Mesón Asiste $nombre" --notes $Notas
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la versión en GitHub.' }

$version = [ordered]@{
  versionCode = $codigo
  versionName = $nombre
  apk         = "https://github.com/wyecg28-ops/meson-asiste/releases/download/$etiqueta/MesonAsiste-$nombre.apk"
  sha256      = $sha
  notas       = $Notas
}
[IO.File]::WriteAllText("$PSScriptRoot\web\version.json", ($version | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))

# git escribe avisos en stderr; con cmd /c no detienen el guion.
cmd /c "git add web/version.json app/build.gradle.kts 2>&1" | Out-Null
cmd /c "git commit -q -m ""Publicar Mesón Asiste $nombre"" 2>&1" | Out-Null
cmd /c "git push -q origin HEAD 2>&1" | Out-Null
cmd /c "git push -q origin HEAD:main 2>&1" | Out-Null
Write-Host "Listo: Mesón Asiste $nombre publicada. Las apps instaladas mostrarán el aviso en unos minutos."

# Builds Editor/fx-jdk: a JDK-21 home whose jmods dir also contains the JavaFX jmods,
# so org.beryx.runtime's jlink can resolve javafx.* for the runtime image / jpackage.
#
# JavaFX jmods are not on Maven Central and openjdk-21.0.2 bundles none. This mirrors the
# real JDK via directory junctions (no admin, no 300MB copy) and drops a merged jmods dir
# (real JDK jmods + JavaFX jmods) beside it. Beryx points jlink at <javaHome>/jmods.
#
# Re-run whenever the JDK moves or JavaFX jmods change. fx-jdk is gitignored.
#
# Usage: powershell -ExecutionPolicy Bypass -File Editor/build_fx_jdk.ps1
param(
  [string]$JdkHome  = 'C:\Program Files\Java\openjdk-21.0.2',       # JDK 21 used to run the app
  [string]$FxJmods  = 'C:\Program Files\Java\openjdk-17.0.14\jmods', # dir containing javafx*.jmod
  [string]$Out      = "$PSScriptRoot\fx-jdk"
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $JdkHome)) { throw "JDK not found: $JdkHome" }
if (-not (Get-ChildItem "$FxJmods\javafx*.jmod" -ErrorAction SilentlyContinue)) { throw "No javafx*.jmod in $FxJmods" }

if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Path $Out | Out-Null

foreach ($d in 'bin','lib','conf','include','legal') {
  $src = Join-Path $JdkHome $d
  if (Test-Path $src) { New-Item -ItemType Junction -Path (Join-Path $Out $d) -Target $src | Out-Null }
}
Copy-Item (Join-Path $JdkHome 'release') (Join-Path $Out 'release') -ErrorAction SilentlyContinue

$jm = Join-Path $Out 'jmods'
New-Item -ItemType Directory -Path $jm | Out-Null
Copy-Item "$JdkHome\jmods\*.jmod" $jm            # real JDK modules (incl. matching java.base)
Copy-Item "$FxJmods\javafx*.jmod"  $jm           # JavaFX modules

$total = (Get-ChildItem "$jm\*.jmod").Count
$fx    = (Get-ChildItem "$jm\javafx*.jmod").Count
Write-Host "fx-jdk ready: $Out  ($total jmods, $fx javafx)"
& "$Out\bin\jlink.exe" --version

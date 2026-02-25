Param(
  [string]$Destination = "$(Split-Path -Parent $PSScriptRoot)\cloudstream-extensions"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-Tool {
  param([string]$Name)
  $exists = $false
  try {
    $null = & $Name --version 2>$null
    $exists = $true
  } catch {}
  if (-not $exists) {
    Write-Error "Required tool '$Name' not found in PATH."
  }
}

Require-Tool git

$repoRoot = Split-Path -Parent $PSScriptRoot
Write-Host "Repo root: $repoRoot"

if (-not (Test-Path $Destination)) {
  Write-Host "Cloning CloudStream extensions template to '$Destination'..."
  & git clone https://github.com/recloudstream/cloudstream-extensions "$Destination"
} else {
  Write-Host "Destination exists: $Destination"
}

$modules = @(
  "Anime4upPack",
  "AnimeBlkomProvider",
  "AnimeiatProvider",
  "ArabSeedProvider",
  "FajerShowProvider",
  "FaselHDProvider",
  "MyCimaProvider",
  "SagaProvider",
  "ShoffreeProvider",
  "CartoonyProvider"
) | Where-Object { Test-Path (Join-Path $repoRoot "$_\src\main\kotlin") }

if ($modules.Count -eq 0) {
  Write-Warning "No source modules found to copy."
}

foreach ($m in $modules) {
  $src = Join-Path $repoRoot $m
  $dst = Join-Path $Destination $m
  Write-Host "Syncing module '$m'..."
  if (Test-Path $dst) {
    Remove-Item -Recurse -Force $dst
  }
  Copy-Item -Recurse -Force $src $dst
}

$settings = Join-Path $Destination "settings.gradle.kts"
if (-not (Test-Path $settings)) {
  Write-Error "settings.gradle.kts not found in destination. Template layout may have changed."
}

$content = Get-Content $settings -Raw
$updated = $false
foreach ($m in $modules) {
  $includeLine = "include(\"`:$m`")"
  if ($content -notmatch [regex]::Escape($includeLine)) {
    Write-Host "Adding include for $m"
    $content += "`n$includeLine"
    $updated = $true
  }
}
if ($updated) {
  Set-Content -Path $settings -Value $content -Encoding UTF8
}

Write-Host ""
Write-Host "Done. Next steps:"
Write-Host "1) cd `"$Destination`""
Write-Host "2) Run gradle wrapper if needed or use bundled gradlew:"
Write-Host "   Windows: .\gradlew.bat :CartoonyProvider:assemble"
Write-Host "   Linux/macOS: ./gradlew :CartoonyProvider:assemble"
Write-Host "3) The .cs3 will be in the module build folder."
Write-Host ""
Write-Host "To build all copied modules, run (example):"
Write-Host "   .\gradlew.bat :FaselHDProvider:assemble :AnimeiatProvider:assemble :CartoonyProvider:assemble"


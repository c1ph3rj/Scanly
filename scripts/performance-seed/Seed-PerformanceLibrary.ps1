#Requires -Version 5.1
<#
.SYNOPSIS
    Seeds a connected Android device with Scanly performance test documents.
#>
param(
    [int]$DocumentCount = 280,
    [string]$LibraryPath = "",
    [string]$PackageName = "in.c1ph3rj.scanly",
    [switch]$SkipLaunch,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$PythonScript = Join-Path $ScriptDir "seed_library.py"
$StagingDir = Join-Path $ScriptDir ".staging"
$SeedOutputDir = Join-Path $StagingDir "seed-output"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Ensure-AdbDevice {
    $devices = (& adb devices) |
        Select-Object -Skip 1 |
        Where-Object { $_.Trim() -match "\tdevice$" }
    if (-not $devices) {
        throw "No adb device connected. Connect a device and enable USB debugging."
    }
    $serial = ($devices[0].Trim() -split "\s+", 2)[0]
    Write-Host "Using device: $serial"
}

function Resolve-LibraryPathFromDatastore {
    param([string]$Package)

    $raw = & adb shell "run-as $Package strings files/datastore/scanly_library_access.preferences_pb" 2>$null
    if (-not $raw) {
        return $null
    }

    $uriLine = $raw | Where-Object { $_ -match "content://com\.android\.externalstorage\.documents/tree/" } | Select-Object -First 1
    if (-not $uriLine) {
        return $null
    }

    $encoded = ($uriLine -replace ".*tree/", "").Trim()
    $decoded = [System.Uri]::UnescapeDataString($encoded)
    $colonIndex = $decoded.IndexOf(":")
    if ($colonIndex -lt 0) {
        return $null
    }

    $volume = $decoded.Substring(0, $colonIndex)
    $relativePath = $decoded.Substring($colonIndex + 1).TrimStart("/")
    if ($volume -eq "primary") {
        return "/storage/emulated/0/$relativePath"
    }
    return "/storage/$volume/$relativePath"
}

function Resolve-LibraryPath {
    param([string]$Override)

    if ($Override) {
        return $Override.TrimEnd("/")
    }

    $resolved = Resolve-LibraryPathFromDatastore -Package $PackageName
    if ($resolved) {
        return $resolved.TrimEnd("/")
    }

    throw "Could not resolve library path. Pass -LibraryPath explicitly."
}

function Ensure-Python {
    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) {
        $python = Get-Command py -ErrorAction SilentlyContinue
    }
    if (-not $python) {
        throw "Python not found. Install Python 3 and Pillow (pip install Pillow)."
    }
    return $python.Source
}

function Ensure-Pillow([string]$PythonExe) {
    & $PythonExe -c "from PIL import Image" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Step "Installing Pillow..."
        & $PythonExe -m pip install Pillow
    }
}

function Pull-ExistingLibraryState {
    param([string]$DeviceLibraryPath)

    New-Item -ItemType Directory -Force -Path $StagingDir | Out-Null
    $markerLocal = Join-Path $StagingDir "library.json"
    $catalogLocal = Join-Path $StagingDir "catalog-existing.json"

    & adb pull "$DeviceLibraryPath/library.json" $markerLocal | Out-Null
    if (-not (Test-Path $markerLocal)) {
        throw "library.json not found at $DeviceLibraryPath"
    }

    $catalogListing = & adb shell "ls $DeviceLibraryPath/catalog/" 2>$null
    $latestCatalog = $catalogListing |
        Where-Object { $_ -match "catalog-r(\d+)\.json" } |
        ForEach-Object {
            if ($_ -match "catalog-r(\d+)\.json") {
                [PSCustomObject]@{
                    Name = $_.Trim()
                    Generation = [int64]$Matches[1]
                }
            }
        } |
        Sort-Object Generation -Descending |
        Select-Object -First 1

    if ($latestCatalog) {
        & adb pull "$DeviceLibraryPath/catalog/$($latestCatalog.Name)" $catalogLocal | Out-Null
    }

    return @{
        Marker = $markerLocal
        Catalog = if (Test-Path $catalogLocal) { $catalogLocal } else { $null }
    }
}

function Invoke-SeedGenerator {
    param(
        [string]$PythonExe,
        [string]$MarkerPath,
        [string]$CatalogPath
    )

    if (Test-Path $SeedOutputDir) {
        Remove-Item -Recurse -Force $SeedOutputDir
    }
    New-Item -ItemType Directory -Force -Path $SeedOutputDir | Out-Null

    $args = @(
        $PythonScript,
        "--output-dir", $SeedOutputDir,
        "--document-count", $DocumentCount,
        "--existing-marker", $MarkerPath
    )
    if ($CatalogPath) {
        $args += @("--existing-catalog", $CatalogPath)
    }

    & $PythonExe @args
    if ($LASTEXITCODE -ne 0) {
        throw "Seed generator failed."
    }
}

function Push-SeedPack {
    param([string]$DeviceLibraryPath)

    $summaryPath = Join-Path $SeedOutputDir "seed-summary.json"
    if (-not (Test-Path $summaryPath)) {
        throw "seed-summary.json missing from generator output."
    }
    $summary = Get-Content $summaryPath -Raw | ConvertFrom-Json

    Write-Host "Pushing $($summary.newDocuments) documents, $($summary.newGroups) groups, $($summary.newPages) pages..."

    $documentsDir = Join-Path $SeedOutputDir "documents"
    if (Test-Path $documentsDir) {
        Get-ChildItem $documentsDir -Directory | ForEach-Object {
            $target = "$DeviceLibraryPath/documents/$($_.Name)"
            & adb push $_.FullName $target
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to push document $($_.Name)"
            }
        }
    }

    $groupsDir = Join-Path $SeedOutputDir "groups"
    if (Test-Path $groupsDir) {
        Get-ChildItem $groupsDir -Directory | ForEach-Object {
            $target = "$DeviceLibraryPath/groups/$($_.Name)"
            & adb push $_.FullName $target
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to push group $($_.Name)"
            }
        }
    }

    $catalogFile = Join-Path $SeedOutputDir $summary.catalogPath
    if (-not (Test-Path $catalogFile)) {
        throw "Catalog file not found: $catalogFile"
    }
    & adb push $catalogFile "$DeviceLibraryPath/catalog/"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to push catalog."
    }

    return $summary
}

function Launch-Scanly {
    & adb shell am force-stop $PackageName | Out-Null
    Start-Sleep -Seconds 1
    & adb shell am start -n "$PackageName/.MainActivity" | Out-Null
}

Write-Step "Checking adb device"
Ensure-AdbDevice

Write-Step "Resolving Scanly library path"
$deviceLibraryPath = Resolve-LibraryPath -Override $LibraryPath
Write-Host "Library path: $deviceLibraryPath"

Write-Step "Pulling existing library state"
$existing = Pull-ExistingLibraryState -DeviceLibraryPath $deviceLibraryPath

Write-Step "Checking Python and Pillow"
$pythonExe = Ensure-Python
Ensure-Pillow -PythonExe $pythonExe

Write-Step "Generating seed pack ($DocumentCount documents)"
Invoke-SeedGenerator -PythonExe $pythonExe -MarkerPath $existing.Marker -CatalogPath $existing.Catalog

if ($DryRun) {
    Write-Host "Dry run complete. Seed pack at: $SeedOutputDir"
    exit 0
}

Write-Step "Pushing seed pack to device"
$summary = Push-SeedPack -DeviceLibraryPath $deviceLibraryPath

Write-Step "Summary"
Write-Host "  New documents: $($summary.newDocuments)"
Write-Host "  New groups:    $($summary.newGroups)"
Write-Host "  New pages:     $($summary.newPages)"
Write-Host "  Total docs:    $($summary.totalDocuments)"
Write-Host "  Catalog gen:   $($summary.generation)"

if (-not $SkipLaunch) {
    Write-Step "Launching Scanly for delta sync"
    Launch-Scanly
    Write-Host "Open the Library tab to verify the new documents."
}
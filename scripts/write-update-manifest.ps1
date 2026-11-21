<#
.SYNOPSIS
  Writes the update manifest and SHA-256 list that Album publishes.

.DESCRIPTION
  version.properties is the single source of truth for the version number. This
  script turns it into the JSON document the app downloads, and writes a
  SHA256SUMS file next to it.

  The manifest's downloadUrl points at the GitHub Release page, not at a
  guessed APK asset name: the release contains three ABI-specific APKs, so a
  single hard-coded file name would 404 for most devices. Each asset entry
  also records its real file name, size and SHA-256 for manual verification.

  Publish the result as a release asset named album-update.json on the release
  tagged v<version>; gradle.properties points ALBUM_UPDATE_URL at
  .../releases/latest/download/album-update.json, which always resolves to the
  newest published manifest.

  The manifest format is the one AppUpdateChecker reads:

    {"versionCode":318,"versionName":"1.3.65","downloadUrl":"https://...","notes":"..."}

  Extra fields (assets, sha256, generatedAt) are ignored by the app and are
  there for humans and for future integrity checks.

.EXAMPLE
  .\scripts\write-update-manifest.ps1 -Notes "Fix the update channel"
#>
[CmdletBinding()]
param(
    [string]$Repository = "Tnomlav/Album",
    [string]$Notes = "",
    [string]$OutputPath,
    [string]$ApkDirectory,
    [string]$ChecksumsPath
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.properties"
if (-not (Test-Path -LiteralPath $versionFile)) {
    throw "version.properties not found at $versionFile"
}

$version = @{}
foreach ($line in Get-Content -LiteralPath $versionFile) {
    if ($line -match '^\s*([A-Z_]+)\s*=\s*(.+?)\s*$') {
        $version[$Matches[1]] = $Matches[2]
    }
}

$major = $version["VERSION_MAJOR"]
$minor = $version["VERSION_MINOR"]
$patch = $version["VERSION_PATCH"]
$code = $version["VERSION_CODE"]
if (-not $major -or -not $minor -or -not $patch -or -not $code) {
    throw "version.properties must define VERSION_CODE, VERSION_MAJOR, VERSION_MINOR and VERSION_PATCH"
}

$versionName = "$major.$minor.$patch"
$tag = "v$versionName"
$releaseUrl = "https://github.com/$Repository/releases/tag/$tag"
$downloadBase = "https://github.com/$Repository/releases/download/$tag"

if (-not $ApkDirectory) {
    $ApkDirectory = Join-Path $root "app/build/outputs/apk/release"
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $root "app/release/album-update.json"
}
if (-not $ChecksumsPath) {
    $ChecksumsPath = Join-Path (Split-Path -Parent $OutputPath) "SHA256SUMS"
}

$expectedAbis = @("arm64-v8a", "armeabi-v7a", "x86_64")
$assets = @()
foreach ($abi in $expectedAbis) {
    $apk = Join-Path $ApkDirectory "app-$abi-release.apk"
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Missing release APK for $abi at $apk. Run assembleRelease first."
    }
    $file = Get-Item -LiteralPath $apk
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $assets += [ordered]@{
        abi    = $abi
        name   = $file.Name
        size   = [long]$file.Length
        sha256 = $hash
        url    = "$downloadBase/$($file.Name)"
    }
}

$sha256 = [ordered]@{}
foreach ($asset in $assets) {
    $sha256.Add($asset.abi, $asset.sha256)
}

$manifest = [ordered]@{
    versionCode = [int]$code
    versionName = $versionName
    downloadUrl = $releaseUrl
    notes       = $Notes
    generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    assets      = $assets
    sha256      = $sha256
}

foreach ($directory in @((Split-Path -Parent $OutputPath), (Split-Path -Parent $ChecksumsPath))) {
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
}

$json = $manifest | ConvertTo-Json -Depth 6
# The app parses the body with org.json; a byte order mark would break it.
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))

[string[]]$checksumLines = @($assets | ForEach-Object { "$($_.sha256)  $($_.name)" })
$checksumText = ($checksumLines -join "`n") + "`n"
[System.IO.File]::WriteAllText($ChecksumsPath, $checksumText, [System.Text.UTF8Encoding]::new($false))

Write-Host "Update manifest written to $OutputPath"
Write-Host "SHA-256 list written to $ChecksumsPath"
Write-Host "  versionCode : $code"
Write-Host "  versionName : $versionName"
Write-Host "  downloadUrl : $releaseUrl"
foreach ($asset in $assets) {
    Write-Host "  $($asset.abi) : $($asset.name) ($($asset.size) bytes, $($asset.sha256))"
}
Write-Host ""
Write-Host "Next: attach album-update.json, SHA256SUMS and the three APKs to the $tag release."

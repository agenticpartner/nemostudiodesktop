# Sets JAVA_HOME for this project using the version in .java-version (or config/java-home).
# Dot-source before running Maven:  . .\set-java.ps1
# Or use .\run.ps1 which does this automatically.

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$VersionFile = Join-Path $ScriptDir ".java-version"
$ConfigHomeFile = Join-Path $ScriptDir "config\java-home"

# Optional: explicit path (e.g. for Windows or when java_home isn't available)
if (Test-Path $ConfigHomeFile) {
    $env:JAVA_HOME = (Get-Content $ConfigHomeFile -Raw).Trim()
    return
}

if (-not (Test-Path $VersionFile)) { return }

$javaVersion = (Get-Content $VersionFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($javaVersion)) { return }

# Windows: try common Adoptium/Temurin paths
$programFiles = $env:ProgramFiles
if (-not $programFiles) { $programFiles = "C:\Program Files" }
$candidates = @(
    (Join-Path $programFiles "Eclipse Adoptium\jdk-$javaVersion*-hotspot"),
    (Join-Path $programFiles "Java\jdk-$javaVersion*"),
    (Join-Path ${env:ProgramFiles(x86)} "Eclipse Adoptium\jdk-$javaVersion*-hotspot")
)
foreach ($pattern in $candidates) {
    $resolved = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($resolved) {
        $env:JAVA_HOME = $resolved.FullName
        return
    }
}

# If JAVA_HOME already set, leave it; otherwise user can create config\java-home with the path

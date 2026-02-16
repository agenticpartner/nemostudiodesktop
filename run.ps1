# Run Nemo Studio (uses Java version from .java-version and Maven Wrapper)
Set-Location $PSScriptRoot
if (Test-Path .\set-java.ps1) { . .\set-java.ps1 }
& .\mvnw.cmd javafx:run

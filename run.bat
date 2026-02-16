@echo off
REM Run Nemo Studio (uses Java from config\java-home if present, then Maven Wrapper)
cd /d "%~dp0"
call set-java.cmd
call mvnw.cmd javafx:run
pause

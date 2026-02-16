@echo off
REM Sets JAVA_HOME for this project. Create config\java-home with the full JDK path (one line, no quotes).
set "CONFIG_HOME=%~dp0config\java-home"
if exist "%CONFIG_HOME%" set /p JAVA_HOME=<"%CONFIG_HOME%"

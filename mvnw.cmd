@echo off
@REM Maven Wrapper for Windows - run without installing Maven (e.g. mvnw.cmd javafx:run)
@REM Requires JAVA_HOME to be set to a JDK 17+ directory.

setlocal

if "%JAVA_HOME%"=="" (
  echo Error: JAVA_HOME is not set. Set it to your JDK installation, e.g. ^
C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Error: JAVA_HOME does not point to a valid JDK: %JAVA_HOME%
  exit /b 1
)

set "EXEC_DIR=%~dp0"
set "WDIR=%EXEC_DIR%"
:findBaseDir
if exist "%WDIR%.mvn" goto baseDirFound
cd ..
if "%WDIR%"=="%CD%" goto baseDirNotFound
set "WDIR=%CD%"
goto findBaseDir

:baseDirFound
set "MAVEN_PROJECTBASEDIR=%WDIR%"
cd /d "%EXEC_DIR%"
goto runWrapper

:baseDirNotFound
set "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"
cd /d "%EXEC_DIR%"

:runWrapper
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven Wrapper...
  set "PROPS=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"
  set "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  if exist "%PROPS%" for /f "usebackq tokens=1,* delims==" %%a in ("%PROPS%") do if "%%a"=="wrapperUrl" set "WRAPPER_URL=%%b"
  mkdir "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper" 2>nul
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
  if errorlevel 1 (
    echo Failed to download Maven Wrapper.
    exit /b 1
  )
)

"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%

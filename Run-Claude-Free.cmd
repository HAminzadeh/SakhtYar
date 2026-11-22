@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "SAKHTYAR_PROJECT_ROOT=%~dp0"
REM The installer creates separate Maven settings for SakhtYar; regular Maven configuration stays untouched.
set "SAKHTYAR_MAVEN_CONFIG=%LOCALAPPDATA%\SakhtYar\Build\maven-central-v2rayn-settings.xml"
set "SAKHTYAR_MAVEN_GLOBAL=%LOCALAPPDATA%\SakhtYar\Build\maven-empty-global-settings.xml"
where mvn.cmd >nul 2>nul
if errorlevel 1 (echo [ERROR] Maven is required. & exit /b 1)
where java.exe >nul 2>nul
if errorlevel 1 (echo [ERROR] Java 17+ is required. & exit /b 1)
call :sakhtyarMaven -q -f "%~dp0claude-context-engine\pom.xml" -DskipTests install dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 (echo [ERROR] Context engine build failed. & exit /b 1)
java -Dfile.encoding=UTF-8 -cp "%~dp0claude-context-engine\target\classes;%~dp0claude-context-engine\target\dependency\*" ir.sakhtyar.context.ContextCli bootstrap "%~dp0."
if errorlevel 1 (echo [ERROR] Local memory MCP registration failed. & exit /b 1)
call :sakhtyarMaven -q -f "%~dp0claude-launcher-ui\pom.xml" -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 (echo [ERROR] JavaFX launcher build failed. & exit /b 1)
java -Dfile.encoding=UTF-8 -cp "%~dp0claude-launcher-ui\target\classes;%~dp0claude-launcher-ui\target\dependency\*" ir.sakhtyar.launcher.LauncherEntry
exit /b %ERRORLEVEL%


:sakhtyarMaven
if exist "%SAKHTYAR_MAVEN_CONFIG%" if exist "%SAKHTYAR_MAVEN_GLOBAL%" (
    call mvn.cmd -s "%SAKHTYAR_MAVEN_CONFIG%" -gs "%SAKHTYAR_MAVEN_GLOBAL%" %*
) else (
    call mvn.cmd %*
)
exit /b %ERRORLEVEL%

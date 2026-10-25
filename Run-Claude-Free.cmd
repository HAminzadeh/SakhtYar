@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "SAKHTYAR_PROJECT_ROOT=%~dp0"
set "CHOICE_FILE=%~dp0_claude_launcher\launch-selection.properties"
if exist "%CHOICE_FILE%" del /q "%CHOICE_FILE%"
where mvn.cmd >nul 2>nul
if errorlevel 1 (echo [ERROR] Maven is required.& exit /b 1)
pushd "%~dp0claude-launcher-ui" || exit /b 1
call mvn.cmd -q javafx:run
set "RESULT=%ERRORLEVEL%"
popd
if not "%RESULT%"=="0" exit /b %RESULT%
if not exist "%CHOICE_FILE%" (echo No session selected.& exit /b 0)
set "LAUNCH_ACTION="
set "LAUNCH_PROFILE="
for /f "usebackq tokens=1,* delims==" %%A in ("%CHOICE_FILE%") do (
  if /i "%%A"=="action" set "LAUNCH_ACTION=%%B"
  if /i "%%A"=="profile" set "LAUNCH_PROFILE=%%B"
)
del /q "%CHOICE_FILE%"
if "%LAUNCH_PROFILE%"=="" exit /b 1
if /i "%LAUNCH_ACTION%"=="CONTINUE" goto continue
if /i "%LAUNCH_ACTION%"=="CHOOSE" goto choose
if /i "%LAUNCH_ACTION%"=="NEW" goto new
echo [ERROR] Invalid action
exit /b 1

:continue
call ccr.cmd "%LAUNCH_PROFILE%" cli -- --continue
exit /b %ERRORLEVEL%
:choose
call ccr.cmd "%LAUNCH_PROFILE%" cli -- --resume
exit /b %ERRORLEVEL%
:new
call ccr.cmd "%LAUNCH_PROFILE%" cli
exit /b %ERRORLEVEL%

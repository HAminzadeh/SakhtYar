@echo off
setlocal DisableDelayedExpansion
set "ENGINE_DIR=%~dp0"
java -Dfile.encoding=UTF-8 -cp "%ENGINE_DIR%target\classes;%ENGINE_DIR%target\dependency\*" ir.sakhtyar.context.ContextMcpServer "%CD%"

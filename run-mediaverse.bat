@echo off
setlocal enabledelayedexpansion

REM Obtener ruta de instalación
set INSTALL_DIR=%~dp0

REM Configurar ruta de Java y Maven
set PATH=%INSTALL_DIR%jdk\bin;%INSTALL_DIR%maven\bin;%PATH%

REM Ir a carpeta de proyecto
cd /d "%INSTALL_DIR%"

REM Ejecutar
echo Iniciando MediaVerse...
echo.
call mvn javafx:run

pause
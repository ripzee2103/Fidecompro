@echo off
cd /d "%~dp0"
if not exist build\fidecompro\Client.class (
 echo Primero ejecute iniciar_servidor.bat para compilar.
 pause
 exit /b 1
)
java -cp build fidecompro.Client
pause

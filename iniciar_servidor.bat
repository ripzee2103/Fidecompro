@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -encoding UTF-8 -d build src\Protocol.java src\Server.java src\Client.java
if errorlevel 1 pause & exit /b 1
java -cp build fidecompro.Server
pause

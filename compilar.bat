@echo off
REM compilar.bat - Wrapper simples: so chama o compilar.ps1 (Docker).
REM Toda a logica de verdade esta no arquivo compilar.ps1, nesta mesma pasta.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compilar.ps1"

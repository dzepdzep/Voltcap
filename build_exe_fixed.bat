@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo  Schichtplaner.exe wird gebaut ...
echo.

REM Python suchen: zuerst python, danach Windows Python Launcher (py)
set "PYTHON_CMD="

python --version >nul 2>nul
if not errorlevel 1 set "PYTHON_CMD=python"

if not defined PYTHON_CMD (
  py -3 --version >nul 2>nul
  if not errorlevel 1 set "PYTHON_CMD=py -3"
)

if not defined PYTHON_CMD (
  echo  Python wurde gefunden, ist aber nicht ueber PATH oder den py-Launcher erreichbar.
  echo.
  echo  Teste in CMD:
  echo    python --version
  echo    py --version
  echo.
  echo  Falls beides nicht funktioniert, Python erneut installieren und
  echo  "Add python.exe to PATH" aktivieren.
  pause
  exit /b 1
)

echo  Python gefunden: %PYTHON_CMD%
%PYTHON_CMD% --version

echo.
echo  [1/3] PyInstaller installieren ...
%PYTHON_CMD% -m pip install --upgrade pyinstaller
if errorlevel 1 (
  echo  Installation fehlgeschlagen.
  pause
  exit /b 1
)

echo.
echo  [2/3] Bibliotheken fuer Offline-PDF/Word laden ...
%PYTHON_CMD% Schichtplaner.py --vendor
if errorlevel 1 (
  echo  Vendor-Download fehlgeschlagen.
  pause
  exit /b 1
)

echo.
echo  [3/3] Programm bauen ...
%PYTHON_CMD% -m PyInstaller --noconfirm --onefile --noconsole --name Schichtplaner ^
  --add-data "Schichtplaner.html;." --add-data "vendor;vendor" --add-data "seed;seed" ^
  Schichtplaner.py

if errorlevel 1 (
  echo  Bauen fehlgeschlagen.
  pause
  exit /b 1
)

echo.
echo  Fertig! Das Programm liegt hier:
echo  %~dp0dist\Schichtplaner.exe
echo.
pause

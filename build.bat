@echo off
setlocal
if exist out rmdir /s /q out
mkdir out
if exist sources.txt del sources.txt
powershell -NoProfile -Command "Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { '\"' + ($_.FullName -replace '\\','/') + '\"' } | Set-Content sources.txt -Encoding ascii"
javac -encoding UTF-8 -source 8 -target 8 -d out @sources.txt
if errorlevel 1 exit /b 1
echo Build completed.

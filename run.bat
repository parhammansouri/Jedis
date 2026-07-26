@echo off
if "%1"=="jedis" (
    shift
    java -cp out Jedis %*
    exit /b %errorlevel%
)
if "%1"=="logrelay" (
    java -cp out LogRelayAnalyzer
    exit /b %errorlevel%
)
if "%1"=="server" (
    shift
    java -cp out server.RelayServer %*
    exit /b %errorlevel%
)
if "%1"=="client" (
    shift
    java -cp out client.RelayClient %*
    exit /b %errorlevel%
)
echo Usage:
echo   run.bat jedis ^<port^>
echo   run.bat logrelay
echo   run.bat server ^<port^>
echo   run.bat client ^<host^> ^<port^>

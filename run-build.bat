@echo off
cd /d "d:\ITC folder\Year4\SEMESTER 2\DevOp\idcard"
mvnw.cmd clean package > build-output.txt 2>&1
if %ERRORLEVEL% == 0 (
    echo BUILD_SUCCESS
) else (
    echo BUILD_FAILED with exit code %ERRORLEVEL%
)

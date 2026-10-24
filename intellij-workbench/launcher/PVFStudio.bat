@echo off
setlocal
if not exist "%~dp0plugins\pvf-studio-workbench\lib\intellij-workbench-*.jar" goto missing_module
if not exist "%~dp0plugins\pvf-studio-workbench\lib\pvf-core-*.jar" goto missing_module
if not exist "%~dp0plugins\pvf-studio-workbench\lib\pvf-workspace-*.jar" goto missing_module
if not exist "%~dp0runtime\jbr\bin\java.exe" goto missing_module
set "IDEA_PROPERTIES=%~dp0pvf-studio.properties"
set "IDEA_JDK=%~dp0runtime\jbr"
if not exist "%USERPROFILE%\.pvf-studio\platform\config" mkdir "%USERPROFILE%\.pvf-studio\platform\config"
call "%~dp0runtime\bin\idea.bat" pvf-studio %*
endlocal
exit /b

:missing_module
echo PVF Studio module or runtime is missing. Please extract the complete archive again. 1>&2
endlocal
exit /b 1

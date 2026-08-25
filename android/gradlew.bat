@echo off
setlocal enabledelayedexpansion
set "DIR=%~dp0"

rem Fall back to the JDK declared in gradle.properties when JAVA_HOME is not configured.
if defined JAVA_HOME goto resolve
for /f "usebackq tokens=1,* delims==" %%a in (`findstr /b "org.gradle.java.home" "%DIR%gradle.properties"`) do set "JAVA_HOME=%%b"

:resolve
set "JAVA_EXE=java"
if exist "!JAVA_HOME!\bin\java.exe" set "JAVA_EXE=!JAVA_HOME!\bin\java.exe"

"!JAVA_EXE!" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal

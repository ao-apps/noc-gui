@echo off

rem
rem Requires Windows NT
rem
if "%OS%" == "Windows_NT" goto nt
    echo This script only works with NT-based versions of Windows.
    goto :eof
:nt
setlocal

rem
rem Find installation directory
rem
set "EXE_DIR=%~dp0%"

rem
rem Make log directory if missing
rem
IF NOT EXIST "%HOMEPATH%\noc-gui" md "%HOMEPATH%\noc-gui"
cd "%HOMEPATH%\noc-gui"

rem
rem Build CLASSPATH
rem
set "CLASSPATH=%EXE_DIR%\noc-gui.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\aocode-public.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\aoserv-client.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\dnsjava-2.0.7.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\MultiSplit.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\mysql-connector-java-3.1.12-bin.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-common.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-monitor-client.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-monitor-portmon.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\noc-monitor.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\ostermillerutils_1_06_00.jar"
set "CLASSPATH=%CLASSPATH%;%EXE_DIR%\lib\postgresql-8.3-605.jdbc3.jar"

rem -Djava.security.debug=access,failure

start javaw ^
    -Xms256M ^
    -Xmx512m ^
    -classpath "%CLASSPATH%" ^
    -ea:com.aoindustries... ^
    -Djava.security.policy="%EXE_DIR%\security.policy.wideopen" ^
    -Djavax.net.ssl.trustStore="%EXE_DIR%\truststore" ^
    com.aoindustries.noc.gui.NOC > "%HOMEPATH%\noc-gui\noc-gui.err"

rem Fuck you, Windows 1986-compatible bullshit batch files. lol   I saw :P

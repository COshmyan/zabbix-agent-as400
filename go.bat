@echo off
set JAVA_HOME=C:\JBuilderX\jdk1.8.0_201
rem set CP=classes;lib\jt400.jar;lib\json-simple-1.1.1.jar
set CP=lib\jt400.jar;lib\json-simple-1.1.1.jar
set SOURCES=src\as400\*.java src\as400\metric\*.java src\as400\thread\*.java src\as400\cache\*.java src\as400\perfstat\*.java
set TARGET=classes
if exist %TARGET%\as400 rmdir %TARGET%\as400 /s /q
del /Q %TARGET%\*.class lib\ZabbixAgent.jar 2>nul
if not exist %TARGET% mkdir %TARGET%
if not exist %TARGET%\as400 mkdir %TARGET%\as400
if not exist %TARGET%\as400\pcml mkdir %TARGET%\as400\pcml
%JAVA_HOME%\bin\javac.exe -source 1.7 -target 1.7 -bootclasspath C:\JBuilderX\jdk1.7\lib\rt.jar -classpath %CP% -d %TARGET% %SOURCES%
%JAVA_HOME%\bin\java.exe -classpath src\as400\pcml;lib\jt400.jar com.ibm.as400.data.ProgramCallDocument -serialize qyaspol
if NOT ERRORLEVEL 1 move qyaspol.pcml.ser %TARGET%\as400\pcml >nul
if NOT ERRORLEVEL 1 %JAVA_HOME%\bin\jar.exe cfm lib\ZabbixAgent.jar Manifest.txt -C %TARGET%\ .
rem if NOT ERRORLEVEL 1 %JAVA_HOME%\bin\java.exe -classpath %CP% -jar lib\ZabbixAgent.jar %1 %2 %3 %4
if NOT ERRORLEVEL 1 %JAVA_HOME%\bin\java.exe -jar lib\ZabbixAgent.jar %1 %2 %3 %4
set SOURCES=
set TARGET=

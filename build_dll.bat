@echo off
rem Builds x64\Release\swe-2.10.03j6f.dll (the JNI library, 64-bit) with MSVC:
rem Swiss Ephemeris sources of swisseph-2026 (upstream master + segment fix) and
rem swejni.c, the same compiler and options as build_swedll.bat of swisseph-2026.
rem
rem   build_dll.bat [ephe dir]
rem
rem Options of swisseph-2026 (official swedll32.vcxproj, Release: /O2 /GF- /Gy /MT
rem /W3 /Gd /wd4996, but /Ob2 instead of /Ob1, whole program optimization /GL
rem /LTCG and profile guided optimization; /Brepro), with three differences:
rem   - x64 (/MACHINE:X64): the JVMs that load this library are 64-bit;
rem   - no /D WIN32: with WIN32 sweodef.h leaves TLS empty, 'swed' becomes one
rem     global shared by every thread and concurrent calls from Java crash;
rem     without it the state is __declspec(thread), as before;
rem   - no /D MAKE_DLL: only the JNI entry points (swejni.c) are exported, the
rem     swe_* functions stay internal, as before.
rem /MT links the C runtime statically: the DLL imports KERNEL32.dll only.
rem
rem Steps:
rem   1. instrumented DLL (/LTCG /GENPROFILE) in obj\x64\pgo\;
rem   2. training run: pgo\SwephExp.java (a Java port of swe_bench.c of
rem      swisseph-2026, through the JNI entry points) in the JVM of %%JAVA_HOME%%;
rem   3. optimized DLL from the same objects (/LTCG /USEPROFILE) in x64\Release\;
rem   4. reference DLL, plain /O2 /Ob1 without /GL, in obj\x64\plain\;
rem   5. check: both workloads of pgo\SwephExp.java (timed and verify) must give
rem      the same checksums on the optimized and on the reference DLL.
rem
rem   set NOPGO=1 & build_dll.bat   - plain /O2 /Ob1 DLL into x64\Release\, no training, no check
rem
rem Needs: Visual Studio 2026 (18) Build Tools with the C++ x64 tools; JAVA_HOME
rem (jni.h, and java.exe for the training run, JDK 11+); an ephe directory with
rem the Swiss Ephemeris files: the argument, else %%SWE_EPHE%%, else ..\swe-java-api\ephe.
rem Run from PowerShell or cmd (not Git Bash): cmd /c "<full path>\build_dll.bat"
rem ASCII only in this file: cmd reads it in the OEM code page.
setlocal
set "LIB_NAME=swe-2.10.03j6f"
set "SRC=%~dp0src"
set "OBJ=%~dp0obj\x64"
set "OUT=%~dp0x64\Release"
set "TRAIN=%~dp0pgo\SwephExp.java"
set "EPHE=%~1"
if "%EPHE%"=="" set "EPHE=%SWE_EPHE%"
if "%EPHE%"=="" set "EPHE=%~dp0..\swe-java-api\ephe"
set "VS=C:\Program Files (x86)\Microsoft Visual Studio"
set "PATH=%PATH%;%VS%\Installer"
set "FILES=swecl.c swedate.c swehel.c swehouse.c swejni.c swejpl.c swemmoon.c swemplan.c sweph.c swephlib.c"
set "OBJS=swecl.obj swedate.obj swehel.obj swehouse.obj swejni.obj swejpl.obj swemmoon.obj swemplan.obj sweph.obj swephlib.obj"
set "CFLAGS=/nologo /c /O2 /GF- /Gy /MT /W3 /Gd /wd4996 /Brepro /D NDEBUG /D _WINDOWS /D _USRDLL /D SWE2JNIDLL_EXPORTS /D _MBCS"
set "LFLAGS=/nologo /DLL /Brepro /SUBSYSTEM:WINDOWS /MACHINE:X64"

if "%JAVA_HOME%"=="" (echo JAVA_HOME is not set & exit /b 1)
if not exist "%JAVA_HOME%\include\jni.h" (echo no jni.h in %JAVA_HOME%\include & exit /b 1)
set "INCL=/I "%JAVA_HOME%\include" /I "%JAVA_HOME%\include\win32""
call "%VS%\18\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64 >nul || exit /b 1
if not exist "%OBJ%\pgo" mkdir "%OBJ%\pgo"
if not exist "%OBJ%\plain" mkdir "%OBJ%\plain"
if not exist "%OUT%" mkdir "%OUT%"
del /q "%OBJ%\*.obj" "%OBJ%\pgo\*.*" "%OBJ%\plain\*.*" 2>nul

if "%NOPGO%"=="1" goto plain

if not exist "%EPHE%\sepl_18.se1" (echo no Swiss Ephemeris files in %EPHE%: needed for the training run & exit /b 1)
if not exist "%JAVA_HOME%\bin\java.exe" (echo no java.exe in %JAVA_HOME%\bin: needed for the training run & exit /b 1)
pushd "%SRC%"
cl %CFLAGS% %INCL% /Ob2 /GL /Fo"%OBJ%\\" %FILES% || (popd & exit /b 1)
popd
pushd "%OBJ%"
link %LFLAGS% /LTCG /GENPROFILE /PGD:pgo\%LIB_NAME%.pgd /OUT:pgo\%LIB_NAME%.dll /IMPLIB:pgo\%LIB_NAME%.lib %OBJS% || (popd & exit /b 1)
popd
"%JAVA_HOME%\bin\java.exe" "%TRAIN%" "%OBJ%\pgo\%LIB_NAME%.dll" "%EPHE%" 1 || exit /b 1
if not exist "%OBJ%\pgo\%LIB_NAME%!1.pgc" (echo the training run wrote no profile & exit /b 1)
pushd "%OBJ%"
link %LFLAGS% /LTCG /USEPROFILE /PGD:pgo\%LIB_NAME%.pgd /OUT:"%OUT%\%LIB_NAME%.dll" /IMPLIB:pgo\%LIB_NAME%.opt.lib %OBJS% || (popd & exit /b 1)
popd

rem reference: plain /O2 /Ob1, separate objects
pushd "%SRC%"
cl %CFLAGS% %INCL% /Ob1 /Fo"%OBJ%\plain\\" %FILES% || (popd & exit /b 1)
popd
pushd "%OBJ%\plain"
link %LFLAGS% /OUT:%LIB_NAME%.dll /IMPLIB:%LIB_NAME%.lib %OBJS% || (popd & exit /b 1)
popd

call :sums "%OUT%\%LIB_NAME%.dll" "%OBJ%\check_opt.txt" || exit /b 1
call :sums "%OBJ%\plain\%LIB_NAME%.dll" "%OBJ%\check_plain.txt" || exit /b 1
echo optimized:
type "%OBJ%\check_opt.txt"
echo reference (plain /Ob1):
type "%OBJ%\check_plain.txt"
fc /b "%OBJ%\check_opt.txt" "%OBJ%\check_plain.txt" >nul || (echo CHECK FAILED: the optimized DLL gives other results than the plain build & exit /b 1)
echo OK: %OUT%\%LIB_NAME%.dll (PGO; same checksums as the plain build)
exit /b 0

:plain
pushd "%SRC%"
cl %CFLAGS% %INCL% /Ob1 /Fo"%OBJ%\\" %FILES% || (popd & exit /b 1)
popd
pushd "%OBJ%"
link %LFLAGS% /OUT:"%OUT%\%LIB_NAME%.dll" /IMPLIB:plain\%LIB_NAME%.lib %OBJS% || (popd & exit /b 1)
popd
echo OK: %OUT%\%LIB_NAME%.dll (plain /O2 /Ob1, no PGO)
exit /b 0

rem :sums <dll> <file> - both checksums of pgo\SwephExp.java, without the timings
:sums
"%JAVA_HOME%\bin\java.exe" "%TRAIN%" "%~1" "%EPHE%" 1 > "%~2.run" || exit /b 1
"%JAVA_HOME%\bin\java.exe" "%TRAIN%" "%~1" "%EPHE%" 1 verify >> "%~2.run" || exit /b 1
(for /f "usebackq tokens=1,2" %%a in ("%~2.run") do echo %%a %%b) > "%~2"
exit /b 0

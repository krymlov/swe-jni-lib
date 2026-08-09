cmake_minimum_required(VERSION 3.17)

project(swe-2.10.03j6f)

set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "System Variable is not defined: JAVA_HOME")
endif()

set(BUILD_ARCH "-m64")
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -Wall")

# -static-libgcc -static: 'swed' is __thread (see the TLS guard in src/sweodef.h), and on MinGW
# that pulls __emutls_get_address out of libgcc_s_seh-1.dll, which in turn wants
# libwinpthread-1.dll. Without a fully static link the produced DLL cannot be loaded unless
# a MinGW bin directory happens to be on PATH - System.loadLibrary() then fails with
# "Can't find dependent libraries". Dropping the missing DLL next to swe-<version>.dll does
# not help: Windows resolves a dependency against the executable's directory, System32 and
# PATH, never against the directory of the DLL being loaded.
SET(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -ffunction-sections -fdata-sections -Wl,--gc-sections -static-libgcc -static")

set(JNI_INCLUDE "$ENV{JAVA_HOME}/include")
set(JNI_INCLUDE_WIN32 "$ENV{JAVA_HOME}/include/win32")

include_directories(${JNI_INCLUDE})
include_directories(${JNI_INCLUDE_WIN32})

add_library(swe-2.10.03j6f SHARED src/swecl.c src/swedate.c src/swehel.c src/swehouse.c src/swejni.c src/swejpl.c src/swemmoon.c src/swemplan.c src/sweph.c src/swephlib.c)

# Build it with w64devkit (x86_64 release), whose bin directory carries its own cmake and
# ninja, so nothing else has to be installed:
#   set PATH=D:\Development\soft\w64devkit_x64\bin;%PATH%
#   cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release && cmake --build build
#
# The toolchain has to be msvcrt-based *and* consistent with its own libwinpthread.a.
# The GCC 15 toolchain in CLion-202x is not: its libwinpthread.a wants
# __intrinsic_setjmpex, which exists only in libucrt.a while the DLL links against msvcrt,
# so -static fails outright and -static-libgcc alone leaves a libwinpthread-1.dll import -
# the "Can't find dependent libraries" problem in a new coat.
#
# Check the import table after any rebuild; only KERNEL32.dll and msvcrt.dll may appear:
#   objdump -p libswe-<version>.dll | grep 'DLL Name:'

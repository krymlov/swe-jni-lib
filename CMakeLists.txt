cmake_minimum_required(VERSION 3.17)

project(swe-2.10.03j6f)

set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "System Variable is not defined: JAVA_HOME")
endif()

set(BUILD_ARCH "-m64")
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -Wall")

# -static-libgcc / -l:libwinpthread.a: 'swed' is __thread (see the TLS guard in src/sweodef.h), and on MinGW
# that pulls __emutls_get_address out of libgcc_s_seh-1.dll. Without this the produced
# DLL cannot be loaded unless a MinGW bin directory happens to be on PATH -
# System.loadLibrary() then fails with "Can't find dependent libraries". Dropping the
# DLL next to swe-<version>.dll does not help: Windows resolves a dependency against the
# executable's directory, System32 and PATH, never against the directory of the DLL
# being loaded.
SET(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -ffunction-sections -fdata-sections -Wl,--gc-sections -static-libgcc")

set(JNI_INCLUDE "$ENV{JAVA_HOME}/include")
set(JNI_INCLUDE_WIN32 "$ENV{JAVA_HOME}/include/win32")

include_directories(${JNI_INCLUDE})
include_directories(${JNI_INCLUDE_WIN32})

add_library(swe-2.10.03j6f SHARED src/swecl.c src/swedate.c src/swehel.c src/swehouse.c src/swejni.c src/swejpl.c src/swemmoon.c src/swemplan.c src/sweph.c src/swephlib.c)

# Static libgcc brings in GCC's emulated TLS, which itself needs pthread, so with the
# GCC 15 toolchain in CLion-202x the DLL ends up importing libwinpthread-1.dll and the
# "Can't find dependent libraries" problem comes back in a new coat. It cannot be linked
# statically with that toolchain: its libwinpthread.a wants __intrinsic_setjmpex, which
# exists only in libucrt.a, while the DLL links against msvcrt - an msvcrt compiler with
# a UCRT-built libwinpthread. Plain -static fails for the same reason.
#
# The DLL that ships in swe-java-lib/libs was produced by the earlier GCC 13 toolchain,
# where -static-libgcc -static linked cleanly and left only KERNEL32 + msvcrt. Check the
# import table after any rebuild:
#   llvm-objdump -p libswe-<version>.dll | grep 'DLL Name:'

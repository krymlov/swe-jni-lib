cmake_minimum_required(VERSION 3.17)

project(swe-2.10.03j6f)

set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "System Variable is not defined: JAVA_HOME")
endif()

set(BUILD_ARCH "-m64")

# Every source below is .c, so CMake compiles them as C and only CMAKE_C_FLAGS applies.
# This used to say CMAKE_CXX_FLAGS, which meant none of it ever reached the compiler - the
# real command line was just "-O3 -DNDEBUG". Kept deliberately short now, because the flags
# that were dead here turned out to be worth measuring rather than restoring; see below.
#
# -fno-math-errno lets GCC emit sqrtsd (and friends) inline instead of calling into libm and
# then branching to set errno. Nothing in this library or above it reads errno - no math
# result changes, only the errno side effect that nobody looks at goes away. Measured on
# this toolchain: 3.6% faster end to end, and slightly smaller.
SET(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-math-errno -Wall")

# What is deliberately NOT here, all of it measured rather than assumed
# (ai-github-projects/swe-jni-lib/tools/opt-experiment.sh reproduces the table):
#
#   -ffast-math / -Ofast     change the numbers. Non-negotiable: swe-java-lib pins two
#                            17-digit constants and the cross-check suites demand exact
#                            agreement with swetest, so one ulp of drift is a broken build,
#                            not a faster one.
#   -march= / -mfma          same reason, less obviously. GCC defaults to
#                            -ffp-contract=fast, which fuses a*b+c into a single fma as
#                            soon as the target has one - a different result. Plain x86-64
#                            has no FMA, which is the only reason this build is currently
#                            safe. Raising the ISA baseline would silently change positions.
#   -mtune=native            numerically safe (tuning picks no new instructions) but it
#                            tunes a shipped library for whichever machine built it, and it
#                            measured no faster here anyway.
#   -ffunction-sections      the classic pairing with --gc-sections, and it makes this DLL
#   -fdata-sections          9.7% BIGGER with no speedup: all 106 JNI entry points are
#                            dllexport roots that between them reach almost every function,
#                            so nothing is collectable and per-section alignment padding is
#                            all that is left. (--gc-sections stays in the link line below;
#                            without these it is a near no-op, but it costs nothing and is
#                            what a future revisit would need.)
#   -flto                    not available: this w64devkit's GCC 16.1 reports "LTO support
#                            has not been enabled in this configuration". Worth re-testing
#                            if the toolchain is ever replaced.
#   -fno-trapping-math       safe here (nothing uses <fenv.h>) but measured indistinguishable
#   -funroll-loops           from the flags above under interleaved A/B, while -funroll-loops
#                            costs 45 KB. Left out rather than carried for noise.

# -static-libgcc -static: 'swed' is __thread (see the TLS guard in src/sweodef.h), and on MinGW
# that pulls __emutls_get_address out of libgcc_s_seh-1.dll, which in turn wants
# libwinpthread-1.dll. Without a fully static link the produced DLL cannot be loaded unless
# a MinGW bin directory happens to be on PATH - System.loadLibrary() then fails with
# "Can't find dependent libraries". Dropping the missing DLL next to swe-<version>.dll does
# not help: Windows resolves a dependency against the executable's directory, System32 and
# PATH, never against the directory of the DLL being loaded.
SET(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -static-libgcc -static")

# Release only: strip the symbol table from the shipped DLL. Exports live in .edata and are
# untouched by this - all 106 JNI entry points still resolve - it only drops the debug/symbol
# tables nothing at runtime consults. Worth 72 KB, about 8% of the file.
SET(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -s")

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

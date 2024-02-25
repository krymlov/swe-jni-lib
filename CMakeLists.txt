cmake_minimum_required(VERSION 3.17)

project(swe-2.10.03j3d)

set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "System Variable is not defined: JAVA_HOME")
endif()

set(BUILD_ARCH "-m64")
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -Wall")
SET(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -ffunction-sections -fdata-sections -Wl,--gc-sections")

set(JNI_INCLUDE "$ENV{JAVA_HOME}/include")
set(JNI_INCLUDE_WIN32 "$ENV{JAVA_HOME}/include/win32")

include_directories(${JNI_INCLUDE})
include_directories(${JNI_INCLUDE_WIN32})

add_library(swe-2.10.03j3d SHARED src/swecl.c src/swedate.c src/swehel.c src/swehouse.c src/swejni.c src/swejpl.c src/swemmoon.c src/swemplan.c src/sweph.c src/swephlib.c)
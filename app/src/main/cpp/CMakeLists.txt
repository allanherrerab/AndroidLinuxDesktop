cmake_minimum_required(VERSION 3.22.1)
project(debian_noroot_native LANGUAGES C)

if (NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Debian Noroot native runtime is built only for arm64-v8a")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
set(XZ_TOOL_XZ OFF CACHE BOOL "Do not build the xz command line tool" FORCE)
set(XZ_TOOL_XZDEC OFF CACHE BOOL "Do not build the xzdec command line tool" FORCE)
set(XZ_TOOL_LZMADEC OFF CACHE BOOL "Do not build the lzmadec command line tool" FORCE)
set(XZ_TOOL_LZMAINFO OFF CACHE BOOL "Do not build the lzmainfo command line tool" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "Do not build dependency tests" FORCE)
FetchContent_Declare(xz
    URL https://codeload.github.com/tukaani-project/xz/tar.gz/refs/tags/v5.8.4
    URL_HASH SHA256=b6b34c52e672e810d339db14e0c10b5b6f5f67a30d8d05cdac5d8cb4d82f78d5)
FetchContent_MakeAvailable(xz)

add_library(debian_native SHARED
    native_launcher.c
    rootfs_extract.c)
target_include_directories(debian_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}
    ${xz_SOURCE_DIR}/src/liblzma/api)
target_link_libraries(debian_native PRIVATE liblzma log)
target_compile_options(debian_native PRIVATE -Wall -Wextra -Werror -Wformat=2)

# Portable first-party policy and algorithms, also built by the host JNI tests.
add_library(debian_core SHARED core/core.c core/sha256.c core/guest_files.c core/jni.c)
target_link_libraries(debian_core PRIVATE m)
target_compile_options(debian_core PRIVATE -Wall -Wextra -Werror -Wformat=2 -ffp-contract=off)

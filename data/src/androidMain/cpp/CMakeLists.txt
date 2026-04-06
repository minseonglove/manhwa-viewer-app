cmake_minimum_required(VERSION 3.22.1)
project(native-libs C CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ===== libarchive =====
set(LIBARCHIVE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/libarchive)

# libarchive 소스 자동 수집 (Windows 전용 및 platform-specific ACL 제외)
file(GLOB LIBARCHIVE_SOURCES "${LIBARCHIVE_DIR}/libarchive/*.c")

# Windows 전용 파일 제외
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*_windows\\.c$")
# FreeBSD ACL 제외 (Android 미지원)
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*acl_freebsd\\.c$")
# Solaris ACL 제외
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*acl_sunos\\.c$")
# Darwin ACL 제외 (Android)
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*acl_darwin\\.c$")
# filter_fork_windows 제외
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*fork_windows\\.c$")
# archive_entry_copy_bhfi (Windows BHFI) 제외
list(FILTER LIBARCHIVE_SOURCES EXCLUDE REGEX ".*copy_bhfi\\.c$")

# libarchive config
configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/libarchive_config.h
    ${CMAKE_CURRENT_BINARY_DIR}/libarchive/config.h
    COPYONLY
)

add_library(archive-native STATIC ${LIBARCHIVE_SOURCES})

target_include_directories(archive-native PRIVATE
    ${LIBARCHIVE_DIR}/libarchive
    ${LIBARCHIVE_DIR}/contrib/android/include
    ${CMAKE_CURRENT_BINARY_DIR}/libarchive
)

target_compile_definitions(archive-native PRIVATE
    HAVE_CONFIG_H=1
    PLATFORM_CONFIG_H="config.h"
)

# libarchive JNI bridge
add_library(archive-jni SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/libarchive_jni.c
)

target_include_directories(archive-jni PRIVATE
    ${LIBARCHIVE_DIR}/libarchive
    ${LIBARCHIVE_DIR}/contrib/android/include
    ${CMAKE_CURRENT_BINARY_DIR}/libarchive
)

target_compile_definitions(archive-jni PRIVATE
    HAVE_CONFIG_H=1
)

target_link_libraries(archive-jni
    archive-native
    log
    z
)

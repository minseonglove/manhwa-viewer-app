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

# libarchive config (separate subdir to avoid conflict with mecab's config.h)
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

# ===== MeCab JNI (기존 유지) =====
set(MECAB_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/mecab-src/mecab/src)
set(MECAB_JAVA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/mecab-src/mecab/java)

set(MECAB_SOURCES
    ${MECAB_SRC_DIR}/char_property.cpp
    ${MECAB_SRC_DIR}/connector.cpp
    ${MECAB_SRC_DIR}/context_id.cpp
    ${MECAB_SRC_DIR}/dictionary.cpp
    ${MECAB_SRC_DIR}/dictionary_compiler.cpp
    ${MECAB_SRC_DIR}/dictionary_generator.cpp
    ${MECAB_SRC_DIR}/dictionary_rewriter.cpp
    ${MECAB_SRC_DIR}/feature_index.cpp
    ${MECAB_SRC_DIR}/iconv_utils.cpp
    ${MECAB_SRC_DIR}/lbfgs.cpp
    ${MECAB_SRC_DIR}/libmecab.cpp
    ${MECAB_SRC_DIR}/nbest_generator.cpp
    ${MECAB_SRC_DIR}/param.cpp
    ${MECAB_SRC_DIR}/string_buffer.cpp
    ${MECAB_SRC_DIR}/tagger.cpp
    ${MECAB_SRC_DIR}/tokenizer.cpp
    ${MECAB_SRC_DIR}/utils.cpp
    ${MECAB_SRC_DIR}/viterbi.cpp
    ${MECAB_SRC_DIR}/writer.cpp
)

set(JNI_WRAPPER_SOURCES
    ${MECAB_JAVA_DIR}/MeCab_wrap.cxx
)

add_library(mecab-jni SHARED
    ${MECAB_SOURCES}
    ${JNI_WRAPPER_SOURCES}
)

# mecab config (separate subdir to avoid conflict with libarchive's config.h)
configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/mecab_config.h
    ${CMAKE_CURRENT_BINARY_DIR}/mecab/config.h
    COPYONLY
)

target_include_directories(mecab-jni PRIVATE
    ${MECAB_SRC_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_BINARY_DIR}/mecab
)

target_compile_definitions(mecab-jni PRIVATE
    HAVE_CONFIG_H=1
)

target_link_libraries(mecab-jni
    log
)

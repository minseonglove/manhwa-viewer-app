// Android NDK configuration for MeCab
// Replaces autotools-generated config.h

#ifndef MECAB_CONFIG_H_
#define MECAB_CONFIG_H_

#define HAVE_DIRENT_H 1
#define HAVE_FCNTL_H 1
#define HAVE_GETENV 1
#define HAVE_MEMORY_H 1
#define HAVE_SETJMP_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1

// Android does not have iconv
// #define HAVE_ICONV 1

#define PACKAGE "mecab"
#define VERSION "0.996"

#define DIC_VERSION 102

#define MECAB_DEFAULT_RC "mecabrc"
#define MECAB_USE_UTF8_ONLY 1
#define MECAB_WITHOUT_SHARE_DIC 1

#endif  // MECAB_CONFIG_H_

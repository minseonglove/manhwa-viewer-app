#ifndef LIBARCHIVE_CONFIG_H_
#define LIBARCHIVE_CONFIG_H_

// Required by archive_digest_private.h and other internal headers
#define __LIBARCHIVE_CONFIG_H_INCLUDED 1

// Android NDK config for libarchive

#define HAVE_DIRENT_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_FCNTL 1
#define HAVE_LIMITS_H 1
#define HAVE_LOCALE_H 1
#define HAVE_SIGNAL_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_WCHAR_H 1
#define HAVE_WCTYPE_H 1
#define HAVE_SYS_IOCTL_H 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_SYS_TIME_H 1
/* HAVE_SYS_UTIME_H not defined - not available on Android */
#define HAVE_TIME_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_GRP_H 1
#define HAVE_PWD_H 1
#define HAVE_SYS_WAIT_H 1
#define HAVE_CTYPE_H 1

// Functions
#define HAVE_CHOWN 1
#define HAVE_FCHDIR 1
#define HAVE_FCHMOD 1
#define HAVE_FCHOWN 1
#define HAVE_FORK 1
#define HAVE_FUTIMENS 1
#define HAVE_GETEUID 1
#define HAVE_GETPID 1
#define HAVE_LCHOWN 1
#define HAVE_LINK 1
#define HAVE_LSTAT 1
#define HAVE_MEMMOVE 1
#define HAVE_MKDIR 1
#define HAVE_MKFIFO 1
#define HAVE_MKNOD 1
#define HAVE_PIPE 1
#define HAVE_POLL 1
#define HAVE_READLINK 1
#define HAVE_READLINKAT 1
#define HAVE_SELECT 1
#define HAVE_SETENV 1
#define HAVE_SETLOCALE 1
#define HAVE_SIGACTION 1
#define HAVE_STRCHR 1
#define HAVE_STRDUP 1
#define HAVE_STRERROR 1
#define HAVE_STRFTIME 1
#define HAVE_STRRCHR 1
#define HAVE_SYMLINK 1
#define HAVE_TIMEGM 1
#define HAVE_TZSET 1
#define HAVE_UNSETENV 1
#define HAVE_UTIME 1
#define HAVE_UTIMES 1
#define HAVE_VFORK 1
#define HAVE_WMEMCMP 1
#define HAVE_WMEMCPY 1
#define HAVE_WMEMMOVE 1
#define HAVE_WCSCPY 1
#define HAVE_WCSLEN 1

// zlib (available in Android NDK)
#define HAVE_ZLIB_H 1
#define HAVE_LIBZ 1

// No iconv on Android
// #define HAVE_ICONV 1
// #define HAVE_ICONV_H 1

// No bzip2, lzma, etc by default on Android
// #define HAVE_BZLIB_H 1
// #define HAVE_LZMA_H 1

// Sizes
#define SIZEOF_WCHAR_T 4

#endif

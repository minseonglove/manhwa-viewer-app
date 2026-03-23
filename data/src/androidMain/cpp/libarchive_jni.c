#include <jni.h>
#include <archive.h>
#include <archive_entry.h>
#include <stdlib.h>
#include <string.h>

// Helper: check if filename has image extension
static int is_image_entry(const char* name) {
    if (!name) return 0;
    const char* dot = strrchr(name, '.');
    if (!dot) return 0;
    dot++; // skip the dot

    // Case-insensitive comparison
    char lower[16];
    int i;
    for (i = 0; i < 15 && dot[i]; i++) {
        lower[i] = (dot[i] >= 'A' && dot[i] <= 'Z') ? dot[i] + 32 : dot[i];
    }
    lower[i] = '\0';

    return strcmp(lower, "jpg") == 0 || strcmp(lower, "jpeg") == 0 ||
           strcmp(lower, "png") == 0 || strcmp(lower, "webp") == 0 ||
           strcmp(lower, "gif") == 0 || strcmp(lower, "bmp") == 0;
}

// Get image entries from archive
JNIEXPORT jobjectArray JNICALL
Java_com_manhwaviewer_data_archive_LibArchiveBridge_nativeGetImageEntries(
    JNIEnv *env, jobject thiz, jstring archivePath) {

    const char *path = (*env)->GetStringUTFChars(env, archivePath, NULL);

    struct archive *a = archive_read_new();
    archive_read_support_format_all(a);
    archive_read_support_filter_all(a);

    // Collect entries
    #define MAX_ENTRIES 10000
    char **entries = (char**)calloc(MAX_ENTRIES, sizeof(char*));
    int count = 0;

    if (archive_read_open_filename(a, path, 10240) == ARCHIVE_OK) {
        struct archive_entry *entry;
        while (archive_read_next_header(a, &entry) == ARCHIVE_OK && count < MAX_ENTRIES) {
            const char *name = archive_entry_pathname(entry);
            if (name && !archive_entry_is_encrypted(entry) && is_image_entry(name)) {
                unsigned char filetype = (archive_entry_filetype(entry) >> 12) & 0xF;
                // 0x4 is directory in octal 04 mask — skip directories
                if (filetype != 4) {
                    entries[count] = strdup(name);
                    count++;
                }
            }
            archive_read_data_skip(a);
        }
    }

    archive_read_free(a);
    (*env)->ReleaseStringUTFChars(env, archivePath, path);

    // Sort entries by filename (bubble sort for simplicity)
    for (int i = 0; i < count - 1; i++) {
        for (int j = 0; j < count - i - 1; j++) {
            if (strcmp(entries[j], entries[j+1]) > 0) {
                char *tmp = entries[j];
                entries[j] = entries[j+1];
                entries[j+1] = tmp;
            }
        }
    }

    // Convert to Java String array
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, stringClass, NULL);

    for (int i = 0; i < count; i++) {
        jstring jstr = (*env)->NewStringUTF(env, entries[i]);
        (*env)->SetObjectArrayElement(env, result, i, jstr);
        (*env)->DeleteLocalRef(env, jstr);
        free(entries[i]);
    }

    free(entries);
    return result;
}

// Get image bytes from archive entry
JNIEXPORT jbyteArray JNICALL
Java_com_manhwaviewer_data_archive_LibArchiveBridge_nativeGetImageBytes(
    JNIEnv *env, jobject thiz, jstring archivePath, jstring entryName) {

    const char *path = (*env)->GetStringUTFChars(env, archivePath, NULL);
    const char *name = (*env)->GetStringUTFChars(env, entryName, NULL);

    // Path traversal guard
    if (strstr(name, "..") != NULL) {
        (*env)->ReleaseStringUTFChars(env, archivePath, path);
        (*env)->ReleaseStringUTFChars(env, entryName, name);
        return NULL;
    }

    struct archive *a = archive_read_new();
    archive_read_support_format_all(a);
    archive_read_support_filter_all(a);

    jbyteArray result = NULL;

    if (archive_read_open_filename(a, path, 10240) == ARCHIVE_OK) {
        struct archive_entry *entry;
        while (archive_read_next_header(a, &entry) == ARCHIVE_OK) {
            if (strcmp(archive_entry_pathname(entry), name) == 0) {
                // Read entry data
                la_int64_t size = archive_entry_size(entry);
                if (size > 0) {
                    char *buf = (char*)malloc((size_t)size);
                    if (buf) {
                        size_t total_read = 0;
                        while ((la_int64_t)total_read < size) {
                            la_ssize_t r = archive_read_data(a, buf + total_read, (size_t)(size - (la_int64_t)total_read));
                            if (r <= 0) break;
                            total_read += (size_t)r;
                        }

                        result = (*env)->NewByteArray(env, (jsize)total_read);
                        (*env)->SetByteArrayRegion(env, result, 0, (jsize)total_read, (jbyte*)buf);
                        free(buf);
                    }
                } else {
                    // Size unknown - read in chunks
                    #define CHUNK_SIZE 65536
                    size_t capacity = CHUNK_SIZE;
                    size_t total = 0;
                    char *buf = (char*)malloc(capacity);

                    if (buf) {
                        la_ssize_t r;
                        while ((r = archive_read_data(a, buf + total, CHUNK_SIZE)) > 0) {
                            total += (size_t)r;
                            if (total + CHUNK_SIZE > capacity) {
                                capacity *= 2;
                                char *new_buf = (char*)realloc(buf, capacity);
                                if (!new_buf) {
                                    free(buf);
                                    buf = NULL;
                                    break;
                                }
                                buf = new_buf;
                            }
                        }

                        if (buf && total > 0) {
                            result = (*env)->NewByteArray(env, (jsize)total);
                            (*env)->SetByteArrayRegion(env, result, 0, (jsize)total, (jbyte*)buf);
                        }
                        free(buf);
                    }
                }
                break;
            }
            archive_read_data_skip(a);
        }
    }

    archive_read_free(a);
    (*env)->ReleaseStringUTFChars(env, archivePath, path);
    (*env)->ReleaseStringUTFChars(env, entryName, name);

    return result;
}

#define _POSIX_C_SOURCE 200809L
#include "rootfs_extract.h"

#include <errno.h>
#include <fcntl.h>
#include <lzma.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define TAR_BLOCK 512U
#define IO_BUFFER (64U * 1024U)
#define NAME_LIMIT 4096U

struct xz_reader {
    int fd;
    lzma_stream stream;
    uint8_t input[IO_BUFFER];
    uint8_t output[IO_BUFFER];
    size_t output_offset;
    size_t output_size;
    int ended;
    int eof;
    char *error;
    size_t error_size;
};

static int set_error(char *error, size_t size, const char *message)
{
    if (error != NULL && size > 0) (void)snprintf(error, size, "%s", message);
    return -1;
}

static int filesystem_error(char *error, size_t size, const char *operation,
                            const char *path)
{
    int failure = errno;
    if (error != NULL && size > 0)
        (void)snprintf(error, size, "%s '%s': %s (errno %d)",
                       operation, path, strerror(failure), failure);
    return -1;
}

static int reader_fill(struct xz_reader *reader)
{
    while (reader->output_offset == reader->output_size && !reader->ended) {
        if (reader->stream.avail_in == 0 && !reader->eof) {
            ssize_t count = read(reader->fd, reader->input, sizeof(reader->input));
            if (count < 0 && errno == EINTR) continue;
            if (count < 0) return set_error(reader->error, reader->error_size,
                                            "Cannot read rootfs archive");
            if (count == 0) reader->eof = 1;
            reader->stream.next_in = reader->input;
            reader->stream.avail_in = (size_t)count;
        }

        reader->stream.next_out = reader->output;
        reader->stream.avail_out = sizeof(reader->output);
        lzma_ret result = lzma_code(&reader->stream, reader->eof ? LZMA_FINISH : LZMA_RUN);
        reader->output_size = sizeof(reader->output) - reader->stream.avail_out;
        reader->output_offset = 0;
        if (result == LZMA_STREAM_END) reader->ended = 1;
        else if (result != LZMA_OK) {
            return set_error(reader->error, reader->error_size,
                             "Corrupt or truncated xz rootfs archive");
        }
    }
    return reader->output_offset < reader->output_size ? 1 : 0;
}

static int reader_read(struct xz_reader *reader, void *destination, size_t length,
                       size_t *received)
{
    uint8_t *out = (uint8_t *)destination;
    size_t done = 0;
    while (done < length) {
        int available = reader_fill(reader);
        if (available < 0) return -1;
        if (available == 0) break;
        size_t count = reader->output_size - reader->output_offset;
        if (count > length - done) count = length - done;
        memcpy(out + done, reader->output + reader->output_offset, count);
        reader->output_offset += count;
        done += count;
    }
    *received = done;
    return 0;
}

static int read_exact(struct xz_reader *reader, void *buffer, size_t size)
{
    size_t received = 0;
    if (reader_read(reader, buffer, size, &received) != 0) return -1;
    if (received != size) return set_error(reader->error, reader->error_size,
                                           "Truncated tar data in rootfs archive");
    return 0;
}

static int skip_bytes(struct xz_reader *reader, uint64_t size)
{
    uint8_t discard[IO_BUFFER];
    while (size > 0) {
        size_t chunk = size > sizeof(discard) ? sizeof(discard) : (size_t)size;
        if (read_exact(reader, discard, chunk) != 0) return -1;
        size -= chunk;
    }
    return 0;
}

static uint64_t parse_octal(const char *text, size_t size, int *valid)
{
    uint64_t value = 0;
    size_t i = 0;
    *valid = 1;
    while (i < size && (text[i] == ' ' || text[i] == '\0')) i++;
    for (; i < size && text[i] != '\0' && text[i] != ' '; i++) {
        if (text[i] < '0' || text[i] > '7' || value > (UINT64_MAX >> 3)) {
            *valid = 0;
            return 0;
        }
        value = (value << 3) + (uint64_t)(text[i] - '0');
    }
    return value;
}

static int valid_header(const unsigned char *header)
{
    int valid = 0;
    uint64_t expected = parse_octal((const char *)header + 148, 8, &valid);
    if (!valid) return 0;
    uint64_t sum = 0;
    for (size_t i = 0; i < TAR_BLOCK; i++)
        sum += (i >= 148 && i < 156) ? (uint64_t)' ' : header[i];
    return sum == expected;
}

static int safe_relative(const char *path)
{
    if (path == NULL || path[0] == '/' || strlen(path) >= NAME_LIMIT) return 0;
    const char *part = path;
    while (*part != '\0') {
        while (*part == '/') part++;
        const char *end = strchr(part, '/');
        size_t length = end == NULL ? strlen(part) : (size_t)(end - part);
        if ((length == 2 && part[0] == '.' && part[1] == '.') ||
            (length == 1 && part[0] == '.')) return 0;
        if (end == NULL) break;
        part = end + 1;
    }
    return 1;
}

static void trim_tar_name(char *path)
{
    size_t length = strlen(path);
    while (length > 0 && path[length - 1] == '\0') length--;
    while (length > 0 && path[length - 1] == '/') path[--length] = '\0';
    while (path[0] == '.' && path[1] == '/') memmove(path, path + 2, strlen(path + 2) + 1);
}

static int make_parent_directories(const char *root, const char *relative)
{
    char path[NAME_LIMIT * 2];
    int length = snprintf(path, sizeof(path), "%s/%s", root, relative);
    if (length < 0 || (size_t)length >= sizeof(path)) return -1;
    size_t root_length = strlen(root);
    for (char *cursor = path + root_length + 1; *cursor != '\0'; cursor++) {
        if (*cursor != '/') continue;
        *cursor = '\0';
        if (mkdir(path, 0755) != 0 && errno != EEXIST) return -1;
        struct stat status;
        // Generated archives enumerate real directories, never children through
        // symlinks. Reject those paths so absolute guest links cannot reach the host.
        if (lstat(path, &status) != 0) return -1;
        if (!S_ISDIR(status.st_mode)) { errno = ENOTDIR; return -1; }
        *cursor = '/';
    }
    return 0;
}

static int remove_existing(const char *path)
{
    struct stat status;
    if (lstat(path, &status) != 0) return errno == ENOENT ? 0 : -1;
    if (S_ISDIR(status.st_mode)) return rmdir(path);
    return unlink(path);
}

static int copy_file_data(struct xz_reader *reader, const char *path,
                          uint64_t size, mode_t mode)
{
    if (size > (uint64_t)INT64_MAX) return -1;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return -1;
    uint8_t buffer[IO_BUFFER];
    uint64_t remaining = size;
    while (remaining > 0) {
        size_t chunk = remaining > sizeof(buffer) ? sizeof(buffer) : (size_t)remaining;
        if (read_exact(reader, buffer, chunk) != 0) {
            close(fd);
            return -1;
        }
        size_t written = 0;
        while (written < chunk) {
            ssize_t count = write(fd, buffer + written, chunk - written);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) {
                close(fd);
                return -1;
            }
            written += (size_t)count;
        }
        remaining -= chunk;
    }
    // PRoot's identity extension emulates guest setuid binaries such as sudo.
    // Actual files remain owned by the unprivileged Android app UID.
    int result = fchmod(fd, mode & 07777);
    if (close(fd) != 0) result = -1;
    return result;
}

static int copy_hardlink(const char *source, const char *destination)
{
    // Android SELinux forbids hard links even within the app's private storage.
    // Materialize the archive alias without following guest symlinks on the host.
    int input = open(source, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (input < 0) return -1;
    struct stat status;
    if (fstat(input, &status) != 0) {
        int failure = errno;
        close(input);
        errno = failure;
        return -1;
    }
    if (!S_ISREG(status.st_mode)) {
        close(input);
        errno = EINVAL;
        return -1;
    }
    int output = open(destination, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (output < 0) {
        int failure = errno;
        close(input);
        errno = failure;
        return -1;
    }
    int result = 0;
    uint8_t buffer[IO_BUFFER];
    for (;;) {
        ssize_t count = read(input, buffer, sizeof(buffer));
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) { result = -1; break; }
        if (count == 0) break;
        size_t written = 0;
        while (written < (size_t)count) {
            ssize_t amount = write(output, buffer + written, (size_t)count - written);
            if (amount < 0 && errno == EINTR) continue;
            if (amount <= 0) {
                if (amount == 0) errno = EIO;
                result = -1;
                break;
            }
            written += (size_t)amount;
        }
        if (result != 0) break;
    }
    if (result == 0) result = fchmod(output, status.st_mode & 07777);
    int failure = errno;
    if (close(output) != 0 && result == 0) { result = -1; failure = errno; }
    if (close(input) != 0 && result == 0) { result = -1; failure = errno; }
    if (result != 0) unlink(destination);
    errno = failure;
    return result;
}

static int write_special(const char *root, const char *relative,
                         const char *link_target, char type, mode_t mode)
{
    char output[NAME_LIMIT * 2];
    char source[NAME_LIMIT * 2];
    int out_len = snprintf(output, sizeof(output), "%s/%s", root, relative);
    if (out_len < 0 || (size_t)out_len >= sizeof(output)) return -1;
    if (type == '5') {
        struct stat status;
        if (lstat(output, &status) == 0 && S_ISDIR(status.st_mode))
            return chmod(output, mode & 07777);
        if (remove_existing(output) != 0) return -1;
        if (mkdir(output, mode & 07777) != 0 && errno != EEXIST) return -1;
        return chmod(output, mode & 07777);
    }
    if (remove_existing(output) != 0) return -1;
    if (type == '2') return symlink(link_target, output);
    if (type == '1') {
        char target[NAME_LIMIT];
        if (snprintf(target, sizeof(target), "%s", link_target) >= (int)sizeof(target)) return -1;
        trim_tar_name(target);
        if (target[0] == '\0' || !safe_relative(target)) { errno = EINVAL; return -1; }
        if (make_parent_directories(root, target) != 0) return -1;
        int source_len = snprintf(source, sizeof(source), "%s/%s", root, target);
        if (source_len < 0 || (size_t)source_len >= sizeof(source)) return -1;
        struct stat status;
        if (lstat(source, &status) != 0) return -1;
        if (!S_ISREG(status.st_mode)) { errno = EINVAL; return -1; }
        if (link(source, output) == 0) return 0;
        if (errno != EPERM && errno != EACCES && errno != EOPNOTSUPP &&
                errno != EXDEV && errno != ENOSYS) return -1;
        return copy_hardlink(source, output);
    }
    return 0;
}

static void field_string(char *destination, size_t destination_size,
                         const unsigned char *source, size_t source_size)
{
    size_t length = 0;
    while (length < source_size && source[length] != '\0') length++;
    if (length >= destination_size) length = destination_size - 1;
    memcpy(destination, source, length);
    destination[length] = '\0';
}

static int read_long_name(struct xz_reader *reader, char *destination,
                          size_t destination_size, uint64_t size)
{
    if (size >= destination_size) return set_error(reader->error, reader->error_size,
                                                   "Rootfs tar path is too long");
    if (read_exact(reader, destination, (size_t)size) != 0) return -1;
    destination[size] = '\0';
    uint64_t padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
    if (padding > 0 && skip_bytes(reader, padding) != 0) return -1;
    trim_tar_name(destination);
    return 0;
}

int rootfs_extract_xz(const char *archive_path, const char *destination,
                      char *error, size_t error_size)
{
    if (error != NULL && error_size > 0) error[0] = '\0';
    if (archive_path == NULL || destination == NULL)
        return set_error(error, error_size, "Missing archive or rootfs destination");

    struct xz_reader reader;
    memset(&reader, 0, sizeof(reader));
    reader.fd = open(archive_path, O_RDONLY | O_CLOEXEC);
    reader.error = error;
    reader.error_size = error_size;
    reader.stream = (lzma_stream)LZMA_STREAM_INIT;
    if (reader.fd < 0) return set_error(error, error_size, "Cannot open rootfs archive");
    lzma_ret init = lzma_stream_decoder(&reader.stream, 256U * 1024U * 1024U, LZMA_CONCATENATED);
    if (init != LZMA_OK) {
        close(reader.fd);
        return set_error(error, error_size, "Cannot initialize xz decoder");
    }
    if (mkdir(destination, 0700) != 0 && errno != EEXIST) {
        lzma_end(&reader.stream);
        close(reader.fd);
        return filesystem_error(error, error_size, "Cannot create rootfs destination", destination);
    }

    unsigned char header[TAR_BLOCK];
    char long_name[NAME_LIMIT] = "";
    char long_link[NAME_LIMIT] = "";
    int result = 0;
    for (;;) {
        size_t received = 0;
        if (reader_read(&reader, header, sizeof(header), &received) != 0) {
            result = -1;
            break;
        }
        if (received == 0) {
            result = set_error(error, error_size, "Missing tar end marker");
            break;
        }
        if (received != sizeof(header)) {
            result = set_error(error, error_size, "Truncated tar header in rootfs archive");
            break;
        }
        int all_zero = 1;
        for (size_t i = 0; i < sizeof(header); i++) if (header[i] != 0) { all_zero = 0; break; }
        if (all_zero) {
            // Drain and verify both the tar padding and the complete xz checksum.
            // Stopping at tar's end marker would accept a damaged xz trailer.
            uint8_t padding[IO_BUFFER];
            uint64_t zero_bytes = TAR_BLOCK;
            do {
                if (reader_read(&reader, padding, sizeof(padding), &received) != 0) {
                    result = -1;
                    break;
                }
                for (size_t i = 0; i < received; i++) {
                    if (padding[i] != 0) {
                        result = set_error(error, error_size, "Nonzero data after tar end marker");
                        break;
                    }
                }
                zero_bytes += received;
            } while (received > 0 && result == 0);
            if (result == 0 && zero_bytes < 2 * TAR_BLOCK)
                result = set_error(error, error_size, "Truncated tar end marker");
            break;
        }
        if (!valid_header(header)) {
            result = set_error(error, error_size, "Bad tar checksum in rootfs archive");
            break;
        }

        int valid = 0;
        uint64_t size = parse_octal((const char *)header + 124, 12, &valid);
        if (!valid) {
            result = set_error(error, error_size, "Invalid tar file size in rootfs archive");
            break;
        }
        mode_t mode = (mode_t)parse_octal((const char *)header + 100, 8, &valid);
        if (!valid) mode = 0644;
        char name[NAME_LIMIT];
        char prefix[256];
        char link_target[NAME_LIMIT];
        field_string(name, sizeof(name), header, 100);
        field_string(prefix, sizeof(prefix), header + 345, 155);
        field_string(link_target, sizeof(link_target), header + 157, 100);
        if (prefix[0] != '\0' && long_name[0] == '\0') {
            char combined[NAME_LIMIT];
            int count = snprintf(combined, sizeof(combined), "%s/%s", prefix, name);
            if (count < 0 || (size_t)count >= sizeof(combined)) {
                result = set_error(error, error_size, "Rootfs tar path is too long");
                break;
            }
            memcpy(name, combined, (size_t)count + 1);
        }

        char type = (char)header[156];
        if (type == 'L' || type == 'K') {
            char *buffer = type == 'L' ? long_name : long_link;
            if (read_long_name(&reader, buffer, NAME_LIMIT, size) != 0) {
                result = -1;
                break;
            }
            continue;
        }
        if (long_name[0] != '\0') {
            memcpy(name, long_name, strlen(long_name) + 1);
            long_name[0] = '\0';
        }
        if (long_link[0] != '\0') {
            memcpy(link_target, long_link, strlen(long_link) + 1);
            long_link[0] = '\0';
        }
        trim_tar_name(name);
        if (type == '5' && (strcmp(name, ".") == 0 || name[0] == '\0')) continue;
        if (name[0] == '\0' || !safe_relative(name)) {
            result = set_error(error, error_size, "Unsafe path in rootfs archive");
            break;
        }
        if (make_parent_directories(destination, name) != 0) {
            result = filesystem_error(error, error_size, "Cannot create rootfs parent directory for", name);
            break;
        }
        char output[NAME_LIMIT * 2];
        int output_len = snprintf(output, sizeof(output), "%s/%s", destination, name);
        if (output_len < 0 || (size_t)output_len >= sizeof(output)) {
            result = set_error(error, error_size, "Rootfs extraction path is too long");
            break;
        }

        if (type == '0' || type == '\0' || type == '7') {
            if (remove_existing(output) != 0 && errno != ENOENT) {
                result = filesystem_error(error, error_size, "Cannot replace rootfs file", name);
                break;
            }
            if (copy_file_data(&reader, output, size, mode) != 0) {
                if (error == NULL || error_size == 0 || error[0] == '\0')
                    filesystem_error(error, error_size, "Cannot write rootfs file", name);
                result = -1;
                break;
            }
        } else if (type == '5' || type == '2' || type == '1') {
            if (write_special(destination, name, link_target, type, mode) != 0) {
                const char *operation = type == '5' ? "Cannot create rootfs directory" :
                        type == '2' ? "Cannot create rootfs symbolic link" :
                                      "Cannot create rootfs hard link or copy";
                result = filesystem_error(error, error_size, operation, name);
                break;
            }
        } else {
            if (skip_bytes(&reader, size) != 0) {
                result = -1;
                break;
            }
        }
        uint64_t padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
        if (padding > 0 && skip_bytes(&reader, padding) != 0) {
            result = -1;
            break;
        }
    }

    lzma_end(&reader.stream);
    close(reader.fd);
    if (result != 0 && (error == NULL || error_size == 0 || error[0] == '\0'))
        return set_error(error, error_size, "Rootfs extraction failed");
    return result;
}

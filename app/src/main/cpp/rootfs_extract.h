#ifndef DEBIAN_NOROOT_ROOTFS_EXTRACT_H
#define DEBIAN_NOROOT_ROOTFS_EXTRACT_H

#include <stddef.h>

int rootfs_extract_xz(const char *archive_path, const char *destination,
                      char *error, size_t error_size);

#endif

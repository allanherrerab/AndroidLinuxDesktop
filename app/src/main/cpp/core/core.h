#ifndef DEBIAN_CORE_H
#define DEBIAN_CORE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

void core_geometry(int vw, int vh, int gw, int gh, const char *mode,
                   float scale, int border, bool aspect43, float result[4]);
bool core_resolution(int width, int height);
float core_pointer_gain(int speed, int acceleration, float distance, float elapsed);
const char *core_language(const char *value);
const char *core_locale(const char *value);
const char *core_keyboard(const char *value);
const char *const *core_regional_codes(int kind, size_t *count);
bool core_timezone_safe(const char *value);
void core_guest_settings(const char *language, const char *keyboard, char result[256]);
int core_key_symbol(int android_code, int unicode);
int core_android_key(int symbol);
int core_shortcut_key(const char *label);

typedef struct {
    uint32_t state[8];
    uint64_t bytes;
    unsigned char block[64];
    size_t used;
} core_sha256;
void core_sha256_init(core_sha256 *hash);
void core_sha256_update(core_sha256 *hash, const unsigned char *data, size_t length);
void core_sha256_final(core_sha256 *hash, char hex[65]);

/* Explicit paths supplied by the app, never a guest-provided cleanup target. */
int core_begin_logs(const char *directory, const char *header);
int core_discard_rootfs(const char *files, const char *name);
int core_initialize_guest(const char *root, int uid, int64_t day);
int core_configure_timezone(const char *root, const char *zone);
int core_configure_resolver(const char *root, const char *hostname,
                            const char *const *servers, size_t count);

#endif

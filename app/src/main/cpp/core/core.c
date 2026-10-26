#define _POSIX_C_SOURCE 200809L
#include "core.h"
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static int clamp(int value, int low, int high)
{
    return value < low ? low : value > high ? high : value;
}

/* Preserve Java Math.min/max NaN and signed-zero behavior across JNI. */
static float minimum(float a, float b)
{
    if (isnan(a) || isnan(b)) return NAN;
    if (a==0 && b==0) return signbit(a) || signbit(b) ? -0.f : 0.f;
    return a<b ? a : b;
}
static float maximum(float a, float b)
{
    if (isnan(a) || isnan(b)) return NAN;
    if (a==0 && b==0) return signbit(a) && signbit(b) ? -0.f : 0.f;
    return a>b ? a : b;
}

void core_geometry(int vw, int vh, int gw, int gh, const char *mode,
                   float scale, int border, bool aspect43, float result[4])
{
    float aw = (float)(vw > 1 ? vw : 1) * (1 - border / 50.f);
    float ah = (float)(vh > 1 ? vh : 1) * (1 - border / 50.f);
    float ratio = aspect43 ? 4.f / 3.f : (float)gw / gh;
    float width = aw, height = ah;
    float fit = minimum(aw / gw, ah / gh);
    if (strcmp(mode, "integer") == 0) {
        scale = maximum(1, floorf(fit));
        width = gw * scale; height = gh * scale;
    } else if (strcmp(mode, "custom") == 0) {
        width = gw * scale; height = gh * scale;
    } else if (strcmp(mode, "stretch") != 0) {
        width = minimum(aw, ah * ratio); height = width / ratio;
    }
    result[0] = (vw - width) / 2; result[1] = (vh - height) / 2;
    result[2] = width; result[3] = height;
}

bool core_resolution(int width, int height)
{
    return width >= 320 && height >= 240 && width <= 7680 && height <= 4320
        && (int64_t)width * height <= 16777216;
}

float core_pointer_gain(int speed, int acceleration, float distance, float elapsed)
{
    static const float speeds[] = {.25f, .5f, 1, 1.5f, 2};
    float velocity = distance / maximum(4, elapsed);
    return speeds[clamp(speed, 0, 4)]
        * (1 + clamp(acceleration, 0, 3) * minimum(2, velocity) * .35f);
}

static const char *const languages[] = {"en", "es", "pt", "fr", "de", "ja", "pl"};
static const char *const locales[] = {"en_US.UTF-8", "es_ES.UTF-8", "pt_BR.UTF-8",
    "fr_FR.UTF-8", "de_DE.UTF-8", "ja_JP.UTF-8", "pl_PL.UTF-8"};
static const char *const keyboards[] = {"us", "us:intl", "gb", "latam", "es", "pt",
    "br", "fr", "de", "jp", "pl", "ca"};

const char *const *core_regional_codes(int kind, size_t *count)
{
    switch (kind) {
        case 0: *count=sizeof(languages)/sizeof(*languages);return languages;
        case 1: *count=sizeof(locales)/sizeof(*locales);return locales;
        case 2: *count=sizeof(keyboards)/sizeof(*keyboards);return keyboards;
        default: *count=0;return NULL;
    }
}

static size_t language_index(const char *value)
{
    for (size_t i = 0; i < sizeof(languages) / sizeof(*languages); i++)
        if (value && strcmp(value, languages[i]) == 0) return i;
    return 0;
}

const char *core_language(const char *value) { return languages[language_index(value)]; }
const char *core_locale(const char *value) { return locales[language_index(value)]; }
const char *core_keyboard(const char *value)
{
    for (size_t i = 0; i < sizeof(keyboards) / sizeof(*keyboards); i++)
        if (value && strcmp(value, keyboards[i]) == 0) return keyboards[i];
    return keyboards[0];
}

bool core_timezone_safe(const char *value)
{
    if (!value || !*value || *value == '/' || strstr(value, "..")) return false;
    for (const unsigned char *p = (const unsigned char *)value; *p; p++)
        if (!((*p >= 'A' && *p <= 'Z') || (*p >= 'a' && *p <= 'z')
              || (*p >= '0' && *p <= '9') || strchr("_+./-", *p))) return false;
    return true;
}

void core_guest_settings(const char *language, const char *keyboard, char result[256])
{
    const char *code = core_language(language), *layout = core_keyboard(keyboard);
    const char *colon = strchr(layout, ':');
    int length = (int)(colon ? colon - layout : (ptrdiff_t)strlen(layout));
    snprintf(result, 256, "NOROOT_LANG='%s'\nNOROOT_LANGUAGE='%s'\n"
             "NOROOT_KEYBOARD_LAYOUT='%.*s'\nNOROOT_KEYBOARD_VARIANT='%s'\n",
             core_locale(code), code, length, layout, colon ? colon + 1 : "");
}

/* Stable Android KeyEvent integer constants; no dependency on an Android SDK
 * header or on the third-party X11 Java/JNI contract. */
static const struct key { int android, symbol; const char *label; } keys[] = {
    {66, 0xff0d, NULL}, {67, 0xff08, NULL}, {112, 0xffff, "Delete"},
    {61, 0xff09, "Tab"}, {111, 0xff1b, "Esc"}, {21, 0xff51, "Left"},
    {19, 0xff52, "Up"}, {22, 0xff53, "Right"}, {20, 0xff54, "Down"},
    {122, 0xff50, NULL}, {123, 0xff57, NULL}, {92, 0xff55, "PgUp"},
    {93, 0xff56, "PgDn"}, {59, 0xffe1, "Shift"}, {60, 0xffe2, NULL},
    {113, 0xffe3, "Ctrl"}, {114, 0xffe4, NULL}, {57, 0xffe9, "Alt"},
    {58, 0xffea, NULL}, {117, 0xffeb, NULL}, {118, 0xffec, NULL},
    {62, 32, "Space"}
};

int core_key_symbol(int android_code, int unicode)
{
    if (android_code >= 131 && android_code <= 142) return 0xffbe + android_code - 131;
    for (size_t i = 0; i < sizeof(keys) / sizeof(*keys); i++)
        /* Space used the Unicode fallback in the original event path. */
        if (keys[i].android == android_code && android_code != 62) return keys[i].symbol;
    return unicode == 0 ? 0 : unicode <= 255 ? unicode : 0x01000000 | unicode;
}

int core_android_key(int symbol)
{
    if (symbol >= 0xffbe && symbol <= 0xffc9) return 131 + symbol - 0xffbe;
    /* Keep the original reverse mapping: other symbols go through Unicode. */
    switch (symbol) {
        case 0xff50: case 0xff57: case 0xff55: case 0xff56:
        case 0xffe2: case 0xffe4: case 0xffea: case 0xffeb: case 0xffec: return 0;
        default: break;
    }
    for (size_t i = 0; i < sizeof(keys) / sizeof(*keys); i++)
        if (keys[i].symbol == symbol) return keys[i].android;
    return 0;
}

int core_shortcut_key(const char *label)
{
    if (!label) return 0;
    for (size_t i = 0; i < sizeof(keys) / sizeof(*keys); i++)
        if (keys[i].label && strcmp(keys[i].label, label) == 0) return keys[i].android;
    if (label[0] == 'F' && label[1] >= '1' && label[1] <= '9') {
        int number = label[1] - '0';
        if (label[2]) {
            if (label[1] != '1' || label[2] < '0' || label[2] > '2' || label[3]) return 0;
            number = 10 + label[2] - '0';
        }
        return 130 + number;
    }
    return 0;
}

static int write_all(int fd, const char *data, size_t size)
{
    while (size) {
        ssize_t count = write(fd, data, size);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return errno ? errno : EIO;
        data += count; size -= (size_t)count;
    }
    return 0;
}

int core_begin_logs(const char *directory, const char *header)
{
    int parent = open(directory, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (parent < 0) return errno;
    const char *names[] = {"desktop.log", "x11.log", "virgl.log"};
    int error = 0;
    for (size_t i = 0; i < sizeof(names) / sizeof(names[0]) && !error; i++) {
        int fd = openat(parent, names[i], O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
        if (fd < 0) { error = errno; break; }
        if (i == 0) error = write_all(fd, header, strlen(header));
        if (close(fd) != 0 && !error) error = errno;
    }
    close(parent);
    return error;
}

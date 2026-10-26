#include "core.h"
#include <assert.h>
#include <string.h>

/* Standalone harness for native sanitizer runs, independent of a JVM. */
int main(void)
{
    core_sha256 hash;char digest[65];
    core_sha256_init(&hash);core_sha256_final(&hash,digest);
    assert(!strcmp(digest,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    core_sha256_init(&hash);
    for (int i=0;i<1000000;i++) core_sha256_update(&hash,(const unsigned char *)"a",1);
    core_sha256_final(&hash,digest);
    assert(!strcmp(digest,"cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"));
    char configuration[256];
    for (int byte=1;byte<256;byte++) {
        char input[256];memset(input,byte,sizeof(input)-1);input[255]=0;
        assert(!strcmp(core_language(input),"en"));
        assert(!strcmp(core_keyboard(input),"us"));
        core_guest_settings(input,input,configuration);
        assert(strstr(configuration,"NOROOT_LANGUAGE='en'\n"));
        (void)core_timezone_safe(input);(void)core_shortcut_key(input);
    }
    for (int code=-100;code<1000;code++) {
        (void)core_key_symbol(code,0x1f600);(void)core_android_key(code);
    }
    assert(!core_resolution(2147483647,2147483647));
    return 0;
}

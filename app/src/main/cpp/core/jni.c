#include "core.h"
#include <jni.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#define JNI_NAME(name) Java_org_debiannoroot_runtime_NativeCore_##name

static void fail(JNIEnv *env, const char *type, const char *message)
{
    if ((*env)->ExceptionCheck(env)) return;
    jclass error = (*env)->FindClass(env, type);
    if (error) (*env)->ThrowNew(env, error, message);
}
static const char *utf(JNIEnv *env, jstring value)
{
    return value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
}
static void release(JNIEnv *env, jstring value, const char *text)
{
    if (text) (*env)->ReleaseStringUTFChars(env, value, text);
}

static void io_error(JNIEnv *env, int error, const char *operation)
{
    if (!error) return;
    char message[256];snprintf(message,sizeof(message),"%s: %s",operation,strerror(error));
    fail(env,"java/io/IOException",message);
}

JNIEXPORT void JNICALL JNI_NAME(initializeGuest)(JNIEnv *env,jclass type,jstring root,jint uid,jlong day)
{
    (void)type;
    if (!root) { fail(env,"java/lang/NullPointerException","root");return; }
    const char *path=utf(env,root);
    if (!path) return;
    int error=core_initialize_guest(path,uid,day);release(env,root,path);
    io_error(env,error,"Cannot initialize Debian account/home");
}

#define FILE_OPERATION(name, native_name, operation) \
JNIEXPORT void JNICALL JNI_NAME(name)(JNIEnv *env,jclass type,jstring root,jstring value) \
{ \
    (void)type; \
    if (!root || !value) { fail(env,"java/lang/NullPointerException","path/value");return; } \
    const char *path=utf(env,root);if (!path) return; \
    const char *text=utf(env,value); \
    if (!text) { release(env,root,path);return; } \
    int error=native_name(path,text);release(env,root,path);release(env,value,text); \
    io_error(env,error,operation); \
}
FILE_OPERATION(discardRootfs,core_discard_rootfs,"Cannot remove staged Debian installation")
FILE_OPERATION(configureTimezone,core_configure_timezone,"Cannot install Debian timezone")

JNIEXPORT void JNICALL JNI_NAME(configureResolver)(JNIEnv *env,jclass type,jstring root,jstring hostname,jobjectArray servers)
{
    (void)type;
    if (!root || !servers) { fail(env,"java/lang/NullPointerException","root/servers");return; }
    const char *path=utf(env,root);
    if (!path) return;
    const char *host=utf(env,hostname);
    if (hostname && !host) { release(env,root,path);return; }
    jsize count=(*env)->GetArrayLength(env,servers);
    char **addresses=calloc(count ? (size_t)count : 1,sizeof(*addresses));
    if (!addresses) { fail(env,"java/lang/OutOfMemoryError","DNS servers");goto done; }
    for (jsize i=0;i<count;i++) {
        jstring value=(*env)->GetObjectArrayElement(env,servers,i);
        if ((*env)->ExceptionCheck(env)) goto cleanup;
        if (!value) { fail(env,"java/lang/NullPointerException","DNS server");goto cleanup; }
        const char *text=utf(env,value);
        if (!text) { (*env)->DeleteLocalRef(env,value);goto cleanup; }
        size_t length=strlen(text)+1;
        addresses[i]=malloc(length);
        if (addresses[i]) memcpy(addresses[i],text,length);
        release(env,value,text);(*env)->DeleteLocalRef(env,value);
        if (!addresses[i]) { fail(env,"java/lang/OutOfMemoryError","DNS server");goto cleanup; }
    }
    io_error(env,core_configure_resolver(path,host,(const char *const *)addresses,(size_t)count),"Cannot configure Debian resolver");
cleanup:
    for (jsize i=0;i<count;i++) free(addresses[i]);
    free(addresses);
done:
    release(env,hostname,host);release(env,root,path);
}

JNIEXPORT jfloatArray JNICALL JNI_NAME(geometry)(JNIEnv *env, jclass type,
    jint vw,jint vh,jint gw,jint gh,jstring mode,jfloat scale,jint border,jboolean aspect43)
{
    (void)type;
    if (!mode) { fail(env,"java/lang/NullPointerException","mode"); return NULL; }
    const char *text = utf(env, mode);
    if (!text) return NULL;
    float values[4]; core_geometry(vw,vh,gw,gh,text,scale,border,aspect43,values);
    release(env,mode,text);
    jfloatArray result = (*env)->NewFloatArray(env,4);
    if (result) (*env)->SetFloatArrayRegion(env,result,0,4,values);
    return result;
}
JNIEXPORT jboolean JNICALL JNI_NAME(validResolution)(JNIEnv *env,jclass type,jint w,jint h)
{ (void)env;(void)type;return core_resolution(w,h); }
JNIEXPORT jfloat JNICALL JNI_NAME(pointerGain)(JNIEnv *env,jclass type,jint speed,jint accel,jfloat distance,jfloat elapsed)
{ (void)env;(void)type;return core_pointer_gain(speed,accel,distance,elapsed); }

#define STRING_LOOKUP(name) \
JNIEXPORT jstring JNICALL JNI_NAME(name)(JNIEnv *env,jclass type,jstring value) \
{ \
    (void)type; const char *text=utf(env,value); \
    if (value && !text) return NULL; \
    jstring result=(*env)->NewStringUTF(env,core_##name(text)); \
    release(env,value,text); return result; \
}
STRING_LOOKUP(language)
STRING_LOOKUP(locale)
STRING_LOOKUP(keyboard)

JNIEXPORT jobjectArray JNICALL JNI_NAME(regionalCodes)(JNIEnv *env,jclass type,jint kind)
{
    (void)type;size_t count;const char *const *codes=core_regional_codes(kind,&count);
    if (!codes) { fail(env,"java/lang/IllegalArgumentException","Unknown regional code table");return NULL; }
    jclass strings=(*env)->FindClass(env,"java/lang/String");
    if (!strings) return NULL;
    jobjectArray result=(*env)->NewObjectArray(env,(jsize)count,strings,NULL);
    if (!result) return NULL;
    for (size_t i=0;i<count;i++) {
        jstring code=(*env)->NewStringUTF(env,codes[i]);
        if (!code) return NULL;
        (*env)->SetObjectArrayElement(env,result,(jsize)i,code);
        (*env)->DeleteLocalRef(env,code);
        if ((*env)->ExceptionCheck(env)) return NULL;
    }
    return result;
}

JNIEXPORT jboolean JNICALL JNI_NAME(timezoneSafe)(JNIEnv *env,jclass type,jstring value)
{
    (void)type; const char *text=utf(env,value);
    if (value && !text) return JNI_FALSE;
    bool result=core_timezone_safe(text); release(env,value,text); return result;
}
JNIEXPORT jstring JNICALL JNI_NAME(guestSettings)(JNIEnv *env,jclass type,jstring language,jstring keyboard)
{
    (void)type; const char *lang=utf(env,language);
    if (language && !lang) return NULL;
    const char *keys=utf(env,keyboard);
    if (keyboard && !keys) { release(env,language,lang);return NULL; }
    char content[256];core_guest_settings(lang,keys,content);
    release(env,language,lang);release(env,keyboard,keys);
    return (*env)->NewStringUTF(env,content);
}
JNIEXPORT jint JNICALL JNI_NAME(keySymbol)(JNIEnv *env,jclass type,jint code,jint unicode)
{ (void)env;(void)type;return core_key_symbol(code,unicode); }
JNIEXPORT jint JNICALL JNI_NAME(androidKey)(JNIEnv *env,jclass type,jint symbol)
{ (void)env;(void)type;return core_android_key(symbol); }
JNIEXPORT jint JNICALL JNI_NAME(shortcutKey)(JNIEnv *env,jclass type,jstring label)
{
    (void)type;const char *text=utf(env,label);
    if (label && !text) return 0;
    int code=core_shortcut_key(text);release(env,label,text);return code;
}

JNIEXPORT void JNICALL JNI_NAME(beginLogs)(JNIEnv *env,jclass type,jstring directory,jstring header)
{
    (void)type;
    if (!directory || !header) { fail(env,"java/lang/NullPointerException","log path/header");return; }
    const char *path=utf(env,directory);
    if (!path) return;
    const char *content=utf(env,header);
    if (!content) { release(env,directory,path);return; }
    int error=core_begin_logs(path,content);
    release(env,directory,path);release(env,header,content);
    if (error) {
        char message[256];snprintf(message,sizeof(message),"Cannot start session logs: %s",strerror(error));
        fail(env,"java/io/IOException",message);
    }
}

/* The stream stays owned by Java (including SAF providers). C owns buffering,
 * hashing, byte limits and cancellation. No raw native handles escape to Java.
 * Region copies avoid pinning a Java heap array during blocking I/O. */
JNIEXPORT void JNICALL JNI_NAME(verifyImage)(JNIEnv *env,jclass type,jobject source,jobject copy,jstring expected,jlong expected_size)
{
    (void)type;
    if (!source) { fail(env,"java/lang/NullPointerException","source");return; }
    const char *checksum=utf(env,expected);
    if (expected && !checksum) return;
    jclass input=(*env)->FindClass(env,"java/io/InputStream");
    if (!input) goto done;
    jclass output=(*env)->FindClass(env,"java/io/OutputStream");
    if (!output) goto done;
    jclass threads=(*env)->FindClass(env,"java/lang/Thread");
    if (!threads) goto done;
    jmethodID read=(*env)->GetMethodID(env,input,"read","([B)I");
    if (!read) goto done;
    jmethodID write=(*env)->GetMethodID(env,output,"write","([BII)V");
    if (!write) goto done;
    jmethodID current=(*env)->GetStaticMethodID(env,threads,"currentThread","()Ljava/lang/Thread;");
    if (!current) goto done;
    jmethodID interrupted=(*env)->GetMethodID(env,threads,"isInterrupted","()Z");
    if (!interrupted) goto done;
    jobject thread=(*env)->CallStaticObjectMethod(env,threads,current);
    if ((*env)->ExceptionCheck(env) || !thread) goto done;
    jbyteArray array=(*env)->NewByteArray(env,65536);
    if (!array) goto done;
    unsigned char bytes[65536];core_sha256 hash;core_sha256_init(&hash);
    int64_t size=0;
    for (;;) {
        jint count=(*env)->CallIntMethod(env,source,read,array);
        if ((*env)->ExceptionCheck(env)) goto done;
        if (count == -1) break;
        if ((*env)->CallBooleanMethod(env,thread,interrupted)) {
            fail(env,"java/io/IOException","Image import cancelled");goto done;
        }
        if ((*env)->ExceptionCheck(env)) goto done;
        if (count < 0 || count > 65536 || size > INT64_MAX - count) {
            fail(env,"java/io/IOException","Invalid image stream length");goto done;
        }
        size+=count;
        if (expected_size > 0 && size > expected_size) {
            fail(env,"java/io/IOException","Image is larger than the expected rootfs asset");goto done;
        }
        (*env)->GetByteArrayRegion(env,array,0,count,(jbyte *)bytes);
        if ((*env)->ExceptionCheck(env)) goto done;
        core_sha256_update(&hash,bytes,(size_t)count);
        if (copy) {
            (*env)->CallVoidMethod(env,copy,write,array,0,count);
            if ((*env)->ExceptionCheck(env)) goto done;
        }
    }
    char actual[65];core_sha256_final(&hash,actual);
    if (!checksum || strcasecmp(actual,checksum) || (expected_size>0 && size!=expected_size))
        fail(env,"java/io/IOException","Rootfs checksum/size mismatch. Select the image distributed with this APK.");
done:
    release(env,expected,checksum);
}

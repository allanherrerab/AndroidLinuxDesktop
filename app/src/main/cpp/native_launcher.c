#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stddef.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <linux/filter.h>
#include <linux/seccomp.h>

#include "rootfs_extract.h"

extern char **environ;

static pthread_mutex_t child_lock = PTHREAD_MUTEX_INITIALIZER;
static pid_t desktop_child = -1;
static int stop_requested;

static char *make_env(const char *key, const char *value)
{
    size_t length = strlen(key) + strlen(value) + 2;
    char *entry = (char *)malloc(length);
    if (entry != NULL) (void)snprintf(entry, length, "%s=%s", key, value);
    return entry;
}

static int key_matches(const char *entry, const char *key)
{
    size_t size = strlen(key);
    return strncmp(entry, key, size) == 0 && entry[size] == '=';
}

static char **build_environment(const char *runtime_bin, const char *tmp_dir,
                                const char *loader, size_t *allocated_count,
                                size_t *allocated_start)
{
    size_t inherited = 0;
    while (environ != NULL && environ[inherited] != NULL) inherited++;
    char **environment = (char **)calloc(inherited + 8, sizeof(char *));
    if (environment == NULL) return NULL;

    char ld_path[4096];
    char tmp[4096];
    char loader_path[4096];
    if (snprintf(ld_path, sizeof(ld_path), "%s", runtime_bin) >= (int)sizeof(ld_path) ||
        snprintf(tmp, sizeof(tmp), "%s", tmp_dir) >= (int)sizeof(tmp) ||
        snprintf(loader_path, sizeof(loader_path), "%s", loader) >= (int)sizeof(loader_path)) {
        free(environment);
        return NULL;
    }

    size_t index = 0;
    for (size_t i = 0; i < inherited; i++) {
        if (key_matches(environ[i], "LD_LIBRARY_PATH") ||
            key_matches(environ[i], "PROOT_TMP_DIR") ||
            key_matches(environ[i], "PROOT_LOADER") ||
            key_matches(environ[i], "PROOT_NO_SECCOMP")) continue;
        environment[index++] = environ[i];
    }
    *allocated_start = index;
    environment[index++] = make_env("LD_LIBRARY_PATH", ld_path);
    environment[index++] = make_env("PROOT_TMP_DIR", tmp);
    environment[index++] = make_env("PROOT_LOADER", loader_path);
    environment[index++] = make_env("PROOT_NO_SECCOMP", "1");
    environment[index] = NULL;
    if (environment[index - 1] == NULL || environment[index - 2] == NULL ||
        environment[index - 3] == NULL || environment[index - 4] == NULL) {
        for (size_t i = *allocated_start; i < index; i++) free(environment[i]);
        free(environment);
        return NULL;
    }
    *allocated_count = index;
    return environment;
}

static void free_environment(char **environment, size_t count, size_t allocated_start)
{
    if (environment == NULL) return;
    for (size_t i = allocated_start; i < count; i++) free(environment[i]);
    free(environment);
}

static int ensure_directory(const char *path, mode_t mode)
{
    if (mkdir(path, mode) == 0 || errno == EEXIST) return 0;
    return -1;
}

static int ensure_proc_version(const char *app_files, char *path, size_t path_size)
{
    int length = snprintf(path, path_size, "%s/proc-version", app_files);
    if (length < 0 || (size_t)length >= path_size) return -1;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) return -1;
    static const char version[] = "Linux version 5.15.0 (debian-noroot@android) #1 SMP\n";
    ssize_t written = write(fd, version, sizeof(version) - 1);
    int result = close(fd);
    return written == (ssize_t)(sizeof(version) - 1) && result == 0 ? 0 : -1;
}

static int append_bind(char **args, size_t *count, const char *host,
                       const char *guest, char *combined, size_t combined_size)
{
    if (access(host, F_OK) != 0) return 0;
    int length = snprintf(combined, combined_size, "%s:%s", host, guest);
    if (length < 0 || (size_t)length >= combined_size) return -1;
    args[(*count)++] = "-b";
    args[(*count)++] = combined;
    return 0;
}

static jint run_desktop(const char *app_files, const char *rootfs,
                        const char *proot, const char *runtime,
                        const char *shared)
{
    char loader[4096];
    char log_path[4096];
    char identity[64];
    char tmp_dir[4096];
    char shm_dir[4096];
    char version_path[4096];
    char bind_shared[8192];
    char bind_version[8192];
    char bind_vendor[8192];
    char bind_odm[8192];
    size_t environment_count = 0;
    size_t allocated_start = 0;
    pid_t child = -1;
    int status = -1;

    if (snprintf(loader, sizeof(loader), "%s/libproot-loader.so", runtime) >= (int)sizeof(loader) ||
        snprintf(log_path, sizeof(log_path), "%s/desktop.log", app_files) >= (int)sizeof(log_path) ||
        snprintf(tmp_dir, sizeof(tmp_dir), "%s/proot-tmp", app_files) >= (int)sizeof(tmp_dir) ||
        snprintf(shm_dir, sizeof(shm_dir), "%s/shared-memory", app_files) >= (int)sizeof(shm_dir))
        return -1;
    if (ensure_directory(tmp_dir, 0700) != 0 ||
        ensure_directory(shm_dir, 0700) != 0 ||
        ensure_proc_version(app_files, version_path, sizeof(version_path)) != 0) return -1;
    if (access(proot, X_OK) != 0 || access(loader, X_OK) != 0) return -1;

    char **environment = build_environment(runtime, tmp_dir, loader,
                                           &environment_count, &allocated_start);
    if (environment == NULL) return -1;

    char *args[64];
    size_t count = 0;
    args[count++] = (char *)proot;
    args[count++] = "--link2symlink";
    args[count++] = "--kill-on-exit";
    args[count++] = "--sysvipc";
    (void)snprintf(identity, sizeof(identity), "%lu:%lu", (unsigned long)getuid(), (unsigned long)getgid());
    args[count++] = "-i";
    args[count++] = identity;
    args[count++] = "-r";
    args[count++] = (char *)rootfs;
    args[count++] = "-w";
    args[count++] = "/home/debian";
    if (append_bind(args, &count, "/dev", "/dev", bind_shared, sizeof(bind_shared)) != 0 ||
        append_bind(args, &count, "/proc", "/proc", bind_version, sizeof(bind_version)) != 0 ||
        append_bind(args, &count, "/sys", "/sys", bind_vendor, sizeof(bind_vendor)) != 0 ||
        append_bind(args, &count, "/system", "/system", bind_odm, sizeof(bind_odm)) != 0) {
        free_environment(environment, environment_count, allocated_start);
        return -1;
    }
    char bind_storage[8192];
    char bind_proc_version[8192];
    char bind_shm[8192];
    if (append_bind(args, &count, shared, "/sdcard", bind_storage, sizeof(bind_storage)) != 0 ||
        append_bind(args, &count, shm_dir, "/dev/shm", bind_shm, sizeof(bind_shm)) != 0 ||
        append_bind(args, &count, version_path, "/proc/version",
                    bind_proc_version, sizeof(bind_proc_version)) != 0) {
        free_environment(environment, environment_count, allocated_start);
        return -1;
    }
    args[count++] = "/usr/bin/env";
    args[count++] = "-i";
    args[count++] = "USER=debian";
    args[count++] = "LOGNAME=debian";
    args[count++] = "HOME=/home/debian";
    args[count++] = "SHELL=/bin/bash";
    args[count++] = "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/games:/usr/games";
    args[count++] = "LANG=C.UTF-8";
    // GLib cannot infer an IANA zone from a copied localtime file. Point both
    // GLib and libc explicitly at the Android-selected guest timezone data.
    args[count++] = "TZ=:/etc/localtime";
    args[count++] = "TERM=xterm-256color";
    args[count++] = "/usr/local/bin/debian-noroot-startx";
    args[count] = NULL;

    if (count >= sizeof(args) / sizeof(args[0])) {
        free_environment(environment, environment_count, allocated_start);
        return -1;
    }
    // posix_spawn is only available starting at API 28. Prepare descriptors in
    // the parent and use only async-signal-safe calls between fork and exec.
    int log_fd = open(log_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    int input_fd = open("/dev/null", O_RDONLY | O_CLOEXEC);
    int exec_error[2] = {-1, -1};
    if (log_fd < 0 || input_fd < 0 || pipe(exec_error) != 0 ||
        fcntl(exec_error[0], F_SETFD, FD_CLOEXEC) < 0 ||
        fcntl(exec_error[1], F_SETFD, FD_CLOEXEC) < 0) {
        if (log_fd >= 0) close(log_fd);
        if (input_fd >= 0) close(input_fd);
        if (exec_error[0] >= 0) close(exec_error[0]);
        if (exec_error[1] >= 0) close(exec_error[1]);
        free_environment(environment, environment_count, allocated_start);
        return -1;
    }
    int spawn_result = 0;
    pthread_mutex_lock(&child_lock);
    if (stop_requested) spawn_result = ECANCELED;
    if (spawn_result == 0) {
        child = fork();
        if (child < 0) spawn_result = errno;
        else if (child == 0) {
            close(exec_error[0]);
            if (setpgid(0, 0) == 0 && dup2(input_fd, STDIN_FILENO) >= 0 &&
                dup2(log_fd, STDOUT_FILENO) >= 0 && dup2(log_fd, STDERR_FILENO) >= 0) {
                close(input_fd);
                close(log_fd);
                execve(proot, args, environment);
            }
            int failure = errno;
            (void)write(exec_error[1], &failure, sizeof(failure));
            _exit(127);
        }
    }
    if (spawn_result == 0) {
        (void)setpgid(child, child);
        desktop_child = child;
    }
    pthread_mutex_unlock(&child_lock);
    close(log_fd);
    close(input_fd);
    close(exec_error[1]);
    free_environment(environment, environment_count, allocated_start);
    if (spawn_result != 0) {
        close(exec_error[0]);
        return -spawn_result;
    }
    ssize_t error_bytes;
    do { error_bytes = read(exec_error[0], &spawn_result, sizeof(spawn_result)); }
    while (error_bytes < 0 && errno == EINTR);
    close(exec_error[0]);

    pid_t waited;
    do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    if (waited < 0) status = -1;
    pthread_mutex_lock(&child_lock);
    if (desktop_child == child) desktop_child = -1;
    pthread_mutex_unlock(&child_lock);
    if (error_bytes > 0) return -spawn_result;
    if (status < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

/* The upstream engine has an auxiliary TCP discovery listener even with X11
 * TCP disabled. Our host uses Binder discovery, so deny Internet sockets on
 * this calling X11 thread and the native server threads it creates. The main
 * app/PRoot process is separate and keeps normal guest Internet access. */
static int restrict_x11_network(void)
{
    struct sock_filter code[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_socket, 0, 4),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AF_INET, 1, 0),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AF_INET6, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };
    struct sock_fprog program = { .len = (unsigned short)(sizeof(code)/sizeof(code[0])), .filter = code };
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0 ||
        prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program) != 0) return -errno;
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_debiannoroot_NativeBridge_restrictX11Network(JNIEnv *env, jclass type)
{
    (void)env; (void)type;
    return restrict_x11_network();
}

JNIEXPORT jstring JNICALL
Java_org_debiannoroot_NativeBridge_extractRootfs(JNIEnv *env, jclass type,
                                                 jstring archive_path,
                                                 jstring destination_path)
{
    (void)type;
    const char *archive = (*env)->GetStringUTFChars(env, archive_path, NULL);
    const char *destination = (*env)->GetStringUTFChars(env, destination_path, NULL);
    if (archive == NULL || destination == NULL) {
        if (archive != NULL) (*env)->ReleaseStringUTFChars(env, archive_path, archive);
        if (destination != NULL) (*env)->ReleaseStringUTFChars(env, destination_path, destination);
        return (*env)->NewStringUTF(env, "Cannot read rootfs path");
    }
    char error[256] = {0};
    int result = rootfs_extract_xz(archive, destination, error, sizeof(error));
    (*env)->ReleaseStringUTFChars(env, archive_path, archive);
    (*env)->ReleaseStringUTFChars(env, destination_path, destination);
    if (result == 0) return (*env)->NewStringUTF(env, "");
    if (error[0] == '\0') (void)snprintf(error, sizeof(error), "Rootfs extraction failed");
    return (*env)->NewStringUTF(env, error);
}

JNIEXPORT jint JNICALL
Java_org_debiannoroot_NativeBridge_runDesktop(JNIEnv *env, jclass type,
                                              jstring app_files_path,
                                              jstring rootfs_path,
                                              jstring proot_path,
                                              jstring runtime_path,
                                              jstring shared_path)
{
    (void)type;
    const char *app_files = (*env)->GetStringUTFChars(env, app_files_path, NULL);
    const char *rootfs = (*env)->GetStringUTFChars(env, rootfs_path, NULL);
    const char *proot = (*env)->GetStringUTFChars(env, proot_path, NULL);
    const char *runtime = (*env)->GetStringUTFChars(env, runtime_path, NULL);
    const char *shared = (*env)->GetStringUTFChars(env, shared_path, NULL);
    jint result = -1;
    if (app_files != NULL && rootfs != NULL && proot != NULL && runtime != NULL && shared != NULL)
        result = run_desktop(app_files, rootfs, proot, runtime, shared);
    if (app_files != NULL) (*env)->ReleaseStringUTFChars(env, app_files_path, app_files);
    if (rootfs != NULL) (*env)->ReleaseStringUTFChars(env, rootfs_path, rootfs);
    if (proot != NULL) (*env)->ReleaseStringUTFChars(env, proot_path, proot);
    if (runtime != NULL) (*env)->ReleaseStringUTFChars(env, runtime_path, runtime);
    if (shared != NULL) (*env)->ReleaseStringUTFChars(env, shared_path, shared);
    return result;
}

JNIEXPORT void JNICALL
Java_org_debiannoroot_NativeBridge_stopDesktop(JNIEnv *env, jclass type)
{
    (void)env;
    (void)type;
    pthread_mutex_lock(&child_lock);
    stop_requested = 1;
    pid_t child = desktop_child;
    if (child > 0 && kill(-child, SIGTERM) != 0) (void)kill(child, SIGTERM);
    pthread_mutex_unlock(&child_lock);
}

JNIEXPORT void JNICALL
Java_org_debiannoroot_NativeBridge_resetSession(JNIEnv *env, jclass type)
{
    (void)env;
    (void)type;
    pthread_mutex_lock(&child_lock);
    if (desktop_child < 0) stop_requested = 0;
    pthread_mutex_unlock(&child_lock);
}


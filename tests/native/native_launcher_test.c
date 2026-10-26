#include "../../app/src/main/cpp/native_launcher.c"
#include <assert.h>
#include <time.h>

struct session_test {
    const char *root;
    const char *executable;
    int result;
};

static void *start_mock(void *argument)
{
    struct session_test *test = argument;
    test->result = run_desktop(test->root, test->root, test->executable, test->root, test->root);
    return NULL;
}

int main(int argc, char **argv)
{
    if (argc > 1 && strcmp(argv[1], "--link2symlink") == 0) {
        for (int i = 1; i < argc; i++) puts(argv[i]);
        assert(getenv("PROOT_NO_SECCOMP") == NULL);
        puts("mock ready");
        fflush(stdout);
        const char *code = getenv("NOROOT_TEST_EXIT");
        if (code != NULL) return atoi(code);
        for (;;) pause();
    }

    pid_t network_child=fork();
    assert(network_child>=0);
    if(network_child==0) {
        assert(restrict_x11_network()==0);
        assert(socket(AF_INET,SOCK_STREAM,0)==-1 && errno==EACCES);
        assert(socket(AF_INET6,SOCK_STREAM,0)==-1 && errno==EACCES);
        int local[2];assert(socketpair(AF_UNIX,SOCK_STREAM,0,local)==0);
        close(local[0]);close(local[1]);_exit(0);
    }
    int policy_status=0;assert(waitpid(network_child,&policy_status,0)==network_child);
    assert(WIFEXITED(policy_status)&&WEXITSTATUS(policy_status)==0);
    int internet=socket(AF_INET,SOCK_STREAM,0);assert(internet>=0);close(internet);

    char root[] = "/tmp/noroot-launcher-XXXXXX";
    assert(mkdtemp(root) != NULL);
    char path[4096];
    (void)snprintf(path, sizeof(path), "%s/libproot-loader.so", root);
    int loader = open(path, O_WRONLY | O_CREAT, 0700);
    assert(loader >= 0);
    close(loader);

    assert(setenv("PROOT_NO_SECCOMP", "1", 1) == 0);
    Java_org_debiannoroot_runtime_NativeBridge_resetSession(NULL, NULL);
    Java_org_debiannoroot_runtime_NativeBridge_stopDesktop(NULL, NULL);
    assert(run_desktop(root, root, argv[0], root, root) == -ECANCELED);

    Java_org_debiannoroot_runtime_NativeBridge_resetSession(NULL, NULL);
    struct session_test test = {root, argv[0], -999};
    pthread_t worker;
    assert(pthread_create(&worker, NULL, start_mock, &test) == 0);
    int ready = 0;
    (void)snprintf(path, sizeof(path), "%s/desktop.log", root);
    for (int attempt = 0; attempt < 250 && !ready; attempt++) {
        FILE *log = fopen(path, "r");
        if (log != NULL) {
            char line[8192];
            while (fgets(line, sizeof(line), log) != NULL)
                if (strstr(line, "mock ready") != NULL) ready = 1;
            fclose(log);
        }
        struct timespec interval = {0, 20000000};
        nanosleep(&interval, NULL);
    }
    assert(ready);
    Java_org_debiannoroot_runtime_NativeBridge_stopDesktop(NULL, NULL);
    assert(pthread_join(worker, NULL) == 0);
    assert(test.result == 128 + SIGTERM);

    Java_org_debiannoroot_runtime_NativeBridge_resetSession(NULL, NULL);
    assert(setenv("NOROOT_TEST_EXIT", "7", 1) == 0);
    assert(run_desktop(root, root, argv[0], root, root) == 7);
    unsetenv("NOROOT_TEST_EXIT");
    const char *files[] = {"desktop.log", "proc-version", "libproot-loader.so"};
    for (size_t i = 0; i < sizeof(files) / sizeof(files[0]); i++) {
        (void)snprintf(path, sizeof(path), "%s/%s", root, files[i]);
        unlink(path);
    }
    (void)snprintf(path, sizeof(path), "%s/proot-tmp", root);
    rmdir(path);
    (void)snprintf(path, sizeof(path), "%s/shared-memory", root);
    rmdir(path);
    rmdir(root);
    puts("Native X11 network isolation, session cancellation, stop, and exit-code tests passed");
    return 0;
}

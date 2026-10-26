#define _POSIX_C_SOURCE 200809L
#include "core.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int write_all(int fd, const void *buffer, size_t size)
{
    const char *data = buffer;
    while (size) {
        ssize_t n = write(fd, data, size);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return errno ? errno : EIO;
        data += n; size -= (size_t)n;
    }
    return 0;
}
static int finish(int fd, int error)
{
    if (close(fd) && !error) return errno;
    return error;
}
static int directory(int parent, const char *name)
{
    if (mkdirat(parent,name,0777) && errno!=EEXIST) return -1;
    return openat(parent,name,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
}
static int remove_entry(int parent, const char *name)
{
    struct stat st;
    if (fstatat(parent,name,&st,AT_SYMLINK_NOFOLLOW)) return errno==ENOENT ? 0 : errno;
    if (!S_ISDIR(st.st_mode)) return unlinkat(parent,name,0) ? errno : 0;
    int fd=openat(parent,name,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (fd<0) return errno;
    DIR *dir=fdopendir(fd);
    if (!dir) { int error=errno;close(fd);return error; }
    int error=0;
    for (;;) {
        errno=0;struct dirent *entry=readdir(dir);
        if (!entry) { error=errno;break; }
        if (!strcmp(entry->d_name,".") || !strcmp(entry->d_name,"..")) continue;
        error=remove_entry(fd,entry->d_name);
        if (error) break;
    }
    if (closedir(dir) && !error) error=errno;
    if (!error && unlinkat(parent,name,AT_REMOVEDIR)) error=errno;
    return error;
}

int core_discard_rootfs(const char *files, const char *name)
{
    if (strcmp(name,"rootfs.installing") && strcmp(name,"rootfs.previous")) return EINVAL;
    int fd=open(files,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (fd<0) return errno;
    return finish(fd,remove_entry(fd,name));
}

static int copy_bytes(int source, int target)
{
    unsigned char buffer[65536];
    for (;;) {
        ssize_t n=read(source,buffer,sizeof(buffer));
        if (n<0 && errno==EINTR) continue;
        if (n<0) return errno;
        if (!n) return 0;
        int error=write_all(target,buffer,(size_t)n);
        if (error) return error;
    }
}

static int copy_skeleton(int source, int destination)
{
    int duplicate=dup(source);
    if (duplicate<0) return errno;
    DIR *dir=fdopendir(duplicate);
    if (!dir) { int error=errno;close(duplicate);return error; }
    int error=0;
    for (;;) {
        errno=0;struct dirent *entry=readdir(dir);
        if (!entry) { error=errno;break; }
        const char *name=entry->d_name;
        if (!strcmp(name,".") || !strcmp(name,"..")) continue;
        struct stat st;
        if (fstatat(source,name,&st,AT_SYMLINK_NOFOLLOW)) { error=errno;break; }
        if (S_ISLNK(st.st_mode)) continue;
        if (S_ISDIR(st.st_mode)) {
            int from=openat(source,name,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
            if (from<0) { error=errno;break; }
            int to=directory(destination,name);
            if (to<0) error=errno;
            else error=finish(to,copy_skeleton(from,to));
            error=finish(from,error);
        } else if (S_ISREG(st.st_mode)) {
            int to=openat(destination,name,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,st.st_mode&0777);
            if (to<0) {
                if (errno==EEXIST) continue;
                error=errno;break;
            }
            int from=openat(source,name,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
            if (from<0) error=errno;
            else error=finish(from,copy_bytes(from,to));
            error=finish(to,error);
        }
        if (error) break;
    }
    if (closedir(dir) && !error) error=errno;
    return error;
}

static int append_account(int etc, const char *name, const char *value)
{
    int fd=openat(etc,name,O_RDWR|O_APPEND|O_CLOEXEC|O_NOFOLLOW);
    if (fd<0) return errno;
    FILE *file=fdopen(fd,"a+");
    if (!file) { int error=errno;close(fd);return error; }
    char *line=NULL;size_t capacity=0;ssize_t length;bool found=false,newline=false;
    while ((length=getline(&line,&capacity,file))>=0) {
        if (!strncmp(line,"debian:",7)) { found=true;break; }
        newline=length>0 && line[length-1]=='\n';
    }
    int error=ferror(file) ? (errno ? errno : EIO) : 0;
    free(line);
    if (!error && !found) {
        if (fseeko(file,0,SEEK_END) || fprintf(file,"%s%s\n",newline ? "" : "\n",value)<0)
            error=errno ? errno : EIO;
    }
    if (fclose(file) && !error) error=errno;
    return error;
}

static int install_sudoers(int directory)
{
    static const char rule[]="debian ALL=(ALL) NOPASSWD: ALL\n";
    int existing=openat(directory,"debian-noroot",O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    if (existing>=0) {
        char content[sizeof(rule)];ssize_t count;
        do { count=read(existing,content,sizeof(content)); } while (count<0 && errno==EINTR);
        if (count<0) { int error=errno;return finish(existing,error); }
        bool same=count==(ssize_t)(sizeof(rule)-1) && !memcmp(content,rule,sizeof(rule)-1);
        if (same) return finish(existing,fchmod(existing,0440) ? errno : 0);
        int error=finish(existing,0);
        if (error) return error;
    } else if (errno!=ENOENT) return errno;
    int fd=openat(directory,"debian-noroot",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0440);
    if (fd<0) return errno;
    int error=write_all(fd,rule,sizeof(rule)-1);
    if (!error && fchmod(fd,0440)) error=errno;
    return finish(fd,error);
}

int core_initialize_guest(const char *root, int uid, int64_t day)
{
    if (uid<0) return EINVAL;
    int base=open(root,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (base<0) return errno;
    int etc=openat(base,"etc",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (etc<0) { int error=errno;return finish(base,error); }
    int homes=directory(base,"home"), home=-1, error=0;
    if (homes<0) error=errno;
    else {
        home=directory(homes,"debian");
        if (home<0) error=errno;
        error=finish(homes,error);
    }
    char value[256];
    if (!error) {
        snprintf(value,sizeof(value),"debian:x:%d:%d:Debian:/home/debian:/bin/bash",uid,uid);
        error=append_account(etc,"passwd",value);
    }
    if (!error) {
        snprintf(value,sizeof(value),"debian:x:%d:",uid);error=append_account(etc,"group",value);
    }
    if (!error) {
        snprintf(value,sizeof(value),"debian:!:%lld:0:99999:7:::",(long long)day);
        error=append_account(etc,"shadow",value);
    }
    if (!error) error=append_account(etc,"gshadow","debian:!::");
    if (!error) {
        int sudoers=openat(etc,"sudoers.d",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
        if (sudoers<0) error=errno;
        else {
            error=finish(sudoers,install_sudoers(sudoers));
        }
    }
    if (!error) {
        int skel=openat(etc,"skel",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
        if (skel<0) {
            if (errno!=ENOENT && errno!=ENOTDIR && errno!=ELOOP) error=errno;
        } else error=finish(skel,copy_skeleton(skel,home));
    }
    const char *folders[]={"Desktop","Documents","Downloads"};
    for (size_t i=0;i<3 && !error;i++) {
        int fd=directory(home,folders[i]);
        if (fd<0) error=errno;else error=finish(fd,0);
    }
    if (home>=0) error=finish(home,error);
    error=finish(etc,error);return finish(base,error);
}

int core_configure_timezone(const char *root, const char *zone)
{
    if (!core_timezone_safe(zone)) return EINVAL;
    int base=open(root,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (base<0) return errno;
    char *path=malloc(strlen(zone)+sizeof("usr/share/zoneinfo/"));
    if (!path) return finish(base,ENOMEM);
    strcpy(path,"usr/share/zoneinfo/");strcat(path,zone);
    /* Debian's zoneinfo aliases are legitimate links; follow those sources. */
    int source=openat(base,path,O_RDONLY|O_CLOEXEC);free(path);
    if (source<0) { int error=errno;return finish(base,error); }
    struct stat st;int error=0;
    if (fstat(source,&st)) error=errno;
    else if (!S_ISREG(st.st_mode)) error=EINVAL;
    int etc=openat(base,"etc",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (etc<0 && !error) error=errno;
    if (!error && unlinkat(etc,"localtime",0) && errno!=ENOENT) error=errno;
    if (!error) {
        int fd=openat(etc,"localtime",O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,st.st_mode&0777);
        if (fd<0) error=errno;else error=finish(fd,copy_bytes(source,fd));
    }
    if (!error) {
        int fd=openat(etc,"timezone",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0666);
        if (fd<0) error=errno;
        else {
            error=write_all(fd,zone,strlen(zone));
            if (!error) error=write_all(fd,"\n",1);
            error=finish(fd,error);
        }
    }
    if (etc>=0) error=finish(etc,error);
    error=finish(source,error);return finish(base,error);
}

static bool hostname_safe(const char *name)
{
    size_t length=name ? strlen(name) : 0;
    if (!length || length>253) return false;
    for (const unsigned char *p=(const unsigned char *)name;*p;p++)
        if (!((*p>='a' && *p<='z') || (*p>='A' && *p<='Z')
              || (*p>='0' && *p<='9') || strchr("._-",*p))) return false;
    return true;
}

static int install_hostname(int etc, const char *hostname)
{
    if (!hostname_safe(hostname)) return 0;
    int fd=openat(etc,"hosts",O_RDWR|O_APPEND|O_CLOEXEC|O_NOFOLLOW);
    if (fd<0) return errno;
    FILE *file=fdopen(fd,"a+");
    if (!file) { int error=errno;close(fd);return error; }
    char *line=NULL;size_t capacity=0;bool found=false;
    while (!found && getline(&line,&capacity,file)>=0) {
        char *save=NULL;
        for (char *word=strtok_r(line," \t\n\r\f\v",&save);word;word=strtok_r(NULL," \t\n\r\f\v",&save))
            if (!strcmp(word,hostname)) { found=true;break; }
    }
    free(line);
    int error=ferror(file) ? (errno ? errno : EIO) : 0;
    if (!error && !found)
        if (fseeko(file,0,SEEK_END) || fprintf(file,"\n127.0.1.1 %s\n",hostname)<0) error=errno ? errno : EIO;
    if (fclose(file) && !error) error=errno;
    return error;
}

int core_configure_resolver(const char *root, const char *hostname,
                            const char *const *servers, size_t count)
{
    /* Values come from Android LinkProperties. Reject line injection if a
     * future caller supplies other data before touching the guest files. */
    for (size_t i=0;i<count;i++) {
        if (!servers[i] || !*servers[i]) return EINVAL;
        for (const unsigned char *p=(const unsigned char *)servers[i];*p;p++)
            if (!((*p>='a' && *p<='z') || (*p>='A' && *p<='Z')
                  || (*p>='0' && *p<='9') || strchr(".:_%+-",*p))) return EINVAL;
    }
    int base=open(root,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (base<0) return errno;
    int etc=openat(base,"etc",O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);
    if (etc<0) { int error=errno;return finish(base,error); }
    int error=install_hostname(etc,hostname);
    if (!error && unlinkat(etc,"resolv.conf",0) && errno!=ENOENT) error=errno;
    if (!error) {
        int fd=openat(etc,"resolv.conf",O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,0666);
        if (fd<0) error=errno;
        else {
            if (!count) {
                static const char fallback[]="nameserver 1.1.1.1\nnameserver 8.8.8.8\n";
                error=write_all(fd,fallback,sizeof(fallback)-1);
            }
            for (size_t i=0;i<count && !error;i++) {
                error=write_all(fd,"nameserver ",11);
                if (!error) error=write_all(fd,servers[i],strlen(servers[i]));
                if (!error) error=write_all(fd,"\n",1);
            }
            error=finish(fd,error);
        }
    }
    error=finish(etc,error);return finish(base,error);
}

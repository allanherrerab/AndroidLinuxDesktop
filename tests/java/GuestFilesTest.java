package org.debiannoroot;

import org.debiannoroot.runtime.NativeCore;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Isolated filesystem fixtures; never touches an installed guest. */
public final class GuestFilesTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Path files=Files.createTempDirectory("noroot-c-files-");
        try {
            Path root=files.resolve("rootfs.installing"),etc=root.resolve("etc"),home=root.resolve("home/debian");
            Files.createDirectories(etc.resolve("sudoers.d"));
            Files.createDirectories(etc.resolve("skel/.config"));
            Files.createDirectories(home);
            Files.writeString(etc.resolve("skel/.config/example"),"default");
            Files.writeString(etc.resolve("skel/keep"),"default must not replace user file");
            Files.writeString(home.resolve("keep"),"user customization");
            Files.writeString(files.resolve("outside"),"do not touch");
            Files.createSymbolicLink(etc.resolve("skel/link"),files.resolve("outside"));
            for(String file:new String[]{"passwd","group","shadow","gshadow"})
                Files.writeString(etc.resolve(file),"root:preserved\n");
            NativeCore.initializeGuest(root.toString(),12345,20000);
            NativeCore.initializeGuest(root.toString(),12345,20001);
            for(String file:new String[]{"passwd","group","shadow","gshadow"}) {
                String text=Files.readString(etc.resolve(file));
                check(text.startsWith("root:preserved\n"));
                check(text.lines().filter(line->line.startsWith("debian:")).count()==1);
            }
            check(Files.readString(etc.resolve("passwd")).contains("debian:x:12345:12345:"));
            check(Files.readString(etc.resolve("shadow")).contains("debian:!:20000:"));
            check(Files.readString(home.resolve("keep")).equals("user customization"));
            check(Files.readString(home.resolve(".config/example")).equals("default"));
            check(!Files.exists(home.resolve("link"),LinkOption.NOFOLLOW_LINKS));
            for(String name:new String[]{"Desktop","Documents","Downloads"})check(Files.isDirectory(home.resolve(name)));
            check(Files.getPosixFilePermissions(etc.resolve("sudoers.d/debian-noroot")).equals(
                java.nio.file.attribute.PosixFilePermissions.fromString("r--r-----")));
            Path zone=root.resolve("usr/share/zoneinfo/Etc");Files.createDirectories(zone);
            byte[] data={84,90,105,102,0,1,2,3};Files.write(zone.resolve("UTC"),data);
            Files.createSymbolicLink(zone.getParent().resolve("UTC"),Path.of("Etc/UTC"));
            Files.createSymbolicLink(etc.resolve("localtime"),files.resolve("outside"));
            NativeCore.configureTimezone(root.toString(),"UTC");
            check(Arrays.equals(Files.readAllBytes(etc.resolve("localtime")),data));
            check(Files.readString(etc.resolve("timezone")).equals("UTC\n"));
            check(Files.readString(files.resolve("outside")).equals("do not touch"));
            Files.writeString(etc.resolve("hosts"),"127.0.0.1 localhost\n# keep customization\n");
            Files.createSymbolicLink(etc.resolve("resolv.conf"),files.resolve("outside"));
            NativeCore.configureResolver(root.toString(),"android-test",new String[]{"192.0.2.1","fe80::1%wlan0"});
            String hosts=Files.readString(etc.resolve("hosts"));
            check(hosts.contains("# keep customization"));check(hosts.contains("127.0.1.1 android-test\n"));
            check(Files.readString(etc.resolve("resolv.conf")).equals("nameserver 192.0.2.1\nnameserver fe80::1%wlan0\n"));
            check(Files.readString(files.resolve("outside")).equals("do not touch"));
            NativeCore.configureResolver(root.toString(),"android-test",new String[0]);
            check(Files.readString(etc.resolve("hosts")).equals(hosts));
            check(Files.readString(etc.resolve("resolv.conf")).equals("nameserver 1.1.1.1\nnameserver 8.8.8.8\n"));
            NativeCore.configureResolver(root.toString(),"invalid\nhost",new String[0]);
            check(Files.readString(etc.resolve("hosts")).equals(hosts));
            try{NativeCore.configureResolver(root.toString(),"test",new String[]{"1.1.1.1\ninject"});throw new AssertionError();}
            catch(IOException expected){}
            try{NativeCore.configureTimezone(root.toString(),"../../outside");throw new AssertionError();}
            catch(IOException expected){}
            Path logs=files.resolve("logs");Files.createDirectory(logs);
            Files.createSymbolicLink(logs.resolve("desktop.log"),files.resolve("outside"));
            try{NativeCore.beginLogs(logs.toString(),"new session\n");throw new AssertionError();}
            catch(IOException expected){}
            check(Files.readString(files.resolve("outside")).equals("do not touch"));
            Files.createSymbolicLink(root.resolve("external"),files);
            try{NativeCore.discardRootfs(files.toString(),"..");throw new AssertionError();}
            catch(IOException expected){}
            NativeCore.discardRootfs(files.toString(),"rootfs.installing");
            check(!Files.exists(root));check(Files.readString(files.resolve("outside")).equals("do not touch"));
            NativeCore.discardRootfs(files.toString(),"rootfs.installing");
            Files.createSymbolicLink(files.resolve("rootfs.previous"),files.resolve("logs"));
            NativeCore.discardRootfs(files.toString(),"rootfs.previous");
            check(Files.isDirectory(logs));
            System.out.println("C guest files: repeatable account/home setup, modes, zone aliases, symlink-safe logs/cleanup and restricted targets passed");
        } finally {
            try(var paths=Files.walk(files)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);
            }
        }
    }
}

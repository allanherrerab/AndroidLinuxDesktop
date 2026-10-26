package org.debiannoroot;

import org.debiannoroot.runtime.NativeCore;
import org.debiannoroot.runtime.DisplayGeometry;
import org.debiannoroot.runtime.ImageIntegrity;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Exercise the production C core through real JNI with CheckJNI enabled. */
public final class NativeCoreTest {
    private static void check(boolean condition) { if(!condition)throw new AssertionError(); }
    private static void near(float actual,float expected) {
        if(Math.abs(actual-expected)>0.002f*Math.max(1,Math.abs(expected)))
            throw new AssertionError(actual+" != "+expected);
    }
    private static String digest(byte[] data)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static void expectSame(IOException expected,InputStream input,OutputStream output)throws Exception {
        try { ImageIntegrity.verify(input,output,digest(new byte[1]),1);throw new AssertionError("Lost I/O failure"); }
        catch(IOException actual){check(actual==expected);}
    }
    private static void hashes()throws Exception {
        Random random=new Random(9221);
        for(int length:new int[]{0,1,2,55,56,57,63,64,65,119,120,127,128,129,65535,65536,65537,1000000}) {
            byte[] data=new byte[length];random.nextBytes(data);
            // Force short reads and occasional legal zero-byte reads at the JNI boundary.
            InputStream input=new ByteArrayInputStream(data){
                int reads;
                @Override public int read(byte[] bytes,int offset,int count){
                    if(++reads%7==0)return 0;
                    return super.read(bytes,offset,Math.min(count,173));
                }
                @Override public int read(byte[] bytes){return read(bytes,0,bytes.length);}
            };
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            ImageIntegrity.verify(input,output,digest(data).toUpperCase(Locale.ROOT),length);
            check(Arrays.equals(data,output.toByteArray()));
            ImageIntegrity.verify(new ByteArrayInputStream(data),null,digest(data),-1);
        }
        byte[] abc="abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ImageIntegrity.verify(new ByteArrayInputStream(abc),null,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",3);
        IOException readError=new IOException("provider failed");
        expectSame(readError,new InputStream(){public int read()throws IOException{throw readError;}},null);
        IOException writeError=new IOException("disk full");
        expectSame(writeError,new ByteArrayInputStream(new byte[1]),
                new OutputStream(){public void write(int value)throws IOException{throw writeError;}});
        Thread.currentThread().interrupt();
        try {
            ImageIntegrity.verify(new ByteArrayInputStream(new byte[1]),null,digest(new byte[1]),1);
            throw new AssertionError("Cancellation ignored");
        } catch(IOException expected){check(Thread.currentThread().isInterrupted());}
        finally {Thread.interrupted();}
        try {
            ImageIntegrity.verify(new ByteArrayInputStream(new byte[2]),null,digest(new byte[2]),1);
            throw new AssertionError("Size limit ignored");
        } catch(IOException expected){check(expected.getMessage().contains("larger"));}
    }
    private static void geometry() {
        Random random=new Random(641);
        for(String mode:new String[]{"fit","stretch","integer","custom"})for(int n=0;n<500;n++) {
            int vw=1+random.nextInt(4000),vh=1+random.nextInt(3000),gw=320+random.nextInt(4000),gh=240+random.nextInt(2000);
            int border=random.nextInt(11);float scale=.25f+random.nextFloat()*3;boolean aspect=random.nextBoolean();
            float aw=Math.max(1,vw)*(1-border/50f),ah=Math.max(1,vh)*(1-border/50f);
            float ratio=aspect?4f/3f:(float)gw/gh,width=aw,height=ah;
            if(mode.equals("integer")){float fit=Math.max(1,(float)Math.floor(Math.min(aw/gw,ah/gh)));width=gw*fit;height=gh*fit;}
            else if(mode.equals("custom")){width=gw*scale;height=gh*scale;}
            else if(!mode.equals("stretch")){width=Math.min(aw,ah*ratio);height=width/ratio;}
            DisplayGeometry actual=DisplayGeometry.compute(vw,vh,gw,gh,mode,scale,border,aspect);
            near(actual.left,(vw-width)/2);near(actual.top,(vh-height)/2);near(actual.width,width);near(actual.height,height);
        }
        for(int speed=-2;speed<7;speed++)for(int accel=-2;accel<6;accel++) {
            float gain=new float[]{.25f,.5f,1,1.5f,2}[Math.max(0,Math.min(4,speed))];
            near(NativeCore.pointerGain(speed,accel,3,0),gain*(1+Math.max(0,Math.min(3,accel))*.75f*.35f));
        }
        check(!NativeCore.validResolution(Integer.MAX_VALUE,Integer.MAX_VALUE));
        check(Float.isNaN(NativeCore.pointerGain(2,0,Float.NaN,16)));
        check(Float.isNaN(NativeCore.pointerGain(2,0,2,Float.NaN)));
    }
    private static void keys() {
        String[] labels={"Esc","Tab","Ctrl","Alt","Shift","Space","Delete","Left","Right","Up","Down","PgUp","PgDn"};
        int[] codes={111,61,113,57,59,62,112,21,22,19,20,92,93};
        for(int i=0;i<labels.length;i++)check(NativeCore.shortcutKey(labels[i])==codes[i]);
        for(int i=0;i<12;i++) {
            check(NativeCore.shortcutKey("F"+(i+1))==131+i);
            check(NativeCore.keySymbol(131+i,0)==0xffbe+i);
            check(NativeCore.androidKey(0xffbe+i)==131+i);
        }
        for(String invalid:new String[]{"","F0","F13","F123","F-1","F1x"})check(NativeCore.shortcutKey(invalid)==0);
        check(NativeCore.shortcutKey(null)==0);
        check(NativeCore.keySymbol(66,0)==0xff0d);
        check(NativeCore.keySymbol(29,0x1f600)==0x0101f600);
        check(NativeCore.keySymbol(29,'a')=='a');check(NativeCore.keySymbol(0,0)==0);
        check(NativeCore.androidKey(0xffe2)==0); // Preserve the original Unicode fallback.
        check(NativeCore.language(null).equals("en"));check(NativeCore.keyboard(null).equals("us"));
        for(String invalid:new String[]{"","../UTC","/etc/passwd","Asia/Tokyo\n","東京","A\u0000B"})
            check(!NativeCore.timezoneSafe(invalid));
    }
    public static void main(String[] args)throws Exception {
        if(args.length==4 && args[0].equals("--file")) {
            try(InputStream image=new FileInputStream(args[1])) {
                ImageIntegrity.verify(image,null,args[2],Long.parseLong(args[3]));
            }
            System.out.println("Production C verifier accepted the complete external image checksum/size");
            return;
        }
        hashes();geometry();keys();
        System.out.println("C core JNI: SHA-256 vectors/boundaries, stream failures/cancellation, 2000 geometry cases and key/regional mappings passed");
    }
}

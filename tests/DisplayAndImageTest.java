package org.debiannoroot;
import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;

public final class DisplayAndImageTest {
    private static void near(float actual,float expected){if(Math.abs(actual-expected)>0.01)throw new AssertionError(actual+" != "+expected);}
    private static String sha(byte[] data)throws Exception{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))result.append(String.format("%02x",b&255));return result.toString();}
    public static void main(String[] args)throws Exception{
        DisplayGeometry fit=DisplayGeometry.compute(1080,1920,1280,800,"fit",1,0,false);
        near(fit.width,1080);near(fit.height,675);near(fit.top,622.5f);
        DisplayGeometry stretch=DisplayGeometry.compute(1080,1920,1280,800,"stretch",1,0,false);near(stretch.height,1920);
        DisplayGeometry integer=DisplayGeometry.compute(1080,1920,1280,800,"integer",1,0,false);near(integer.width,1280);near(integer.left,-100);
        DisplayGeometry two=DisplayGeometry.compute(3000,2000,1280,800,"integer",1,0,false);near(two.width,2560);near(two.height,1600);
        DisplayGeometry custom=DisplayGeometry.compute(1080,1920,1280,800,"custom",1.5f,0,false);near(custom.width,1920);near(custom.left,-420);
        DisplayGeometry ratio=DisplayGeometry.compute(1920,1080,1280,800,"fit",1,5,true);near(ratio.width/ratio.height,4f/3f);
        if(!DisplayGeometry.validResolution(1920,1080)||DisplayGeometry.validResolution(99999,99999)||DisplayGeometry.validResolution(100,100)||DisplayGeometry.validResolution(7680,4320))throw new AssertionError("Resolution bound failure");
        near(DisplayGeometry.pointerGain(2,0,200,1),1);
        if(DisplayGeometry.pointerGain(2,3,200,10)<=DisplayGeometry.pointerGain(2,3,2,100))throw new AssertionError("Acceleration is not speed-sensitive");
        byte[] image=new byte[180001];for(int i=0;i<image.length;i++)image[i]=(byte)(i*17);
        ByteArrayOutputStream copy=new ByteArrayOutputStream();ImageIntegrity.verify(new ByteArrayInputStream(image),copy,sha(image),image.length);
        if(!Arrays.equals(image,copy.toByteArray()))throw new AssertionError("Image import changed data");
        for(byte[] corrupt:new byte[][]{Arrays.copyOf(image,image.length-1),Arrays.copyOf(image,image.length+1),new byte[image.length]}){
            boolean failed=false;try{ImageIntegrity.verify(new ByteArrayInputStream(corrupt),null,sha(image),image.length);}catch(IOException expected){failed=true;}if(!failed)throw new AssertionError("Corrupt image accepted");
        }
        System.out.println("Display geometry, integer/custom cropping, pointer acceleration, and streamed image integrity tests passed");
    }
}

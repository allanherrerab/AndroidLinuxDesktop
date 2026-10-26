import java.io.*;
import java.security.*;
import java.security.cert.*;
class MakeTrustStore { public static void main(String[] args) throws Exception {
KeyStore ks=KeyStore.getInstance("JKS"); ks.load(null,null); int i=0;
try(InputStream in=new FileInputStream("/etc/ssl/certs/ca-certificates.crt")) {
for(java.security.cert.Certificate c:CertificateFactory.getInstance("X.509").generateCertificates(in)) ks.setCertificateEntry("system-"+(i++),c); }
try(OutputStream out=new FileOutputStream(args[0])) {ks.store(out,"changeit".toCharArray());}
}}

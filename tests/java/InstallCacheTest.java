package org.zhixiaoce.aperture;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class InstallCacheTest {
    static int checks;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    static class Source implements InstallCache.Source {
        final long declared; final byte[] bytes; int position; boolean closed; boolean fail;
        Source(long declared,byte[] bytes){this.declared=declared;this.bytes=bytes;}
        public long size(){return declared;}
        public int read(byte[] target)throws IOException{if(fail)throw new IOException("offline");if(position==bytes.length)return -1;int n=Math.min(target.length,bytes.length-position);System.arraycopy(bytes,position,target,0,n);position+=n;return n;}
        public void close(){closed=true;}
    }
    static void rejected(File root,Source source,boolean cancel)throws Exception{
        try{InstallCache.copy(root,source,()->cancel);throw new AssertionError("accepted invalid source");}catch(IOException expected){}
        check(source.closed,"source closed after rejection");check(root.listFiles()==null||root.listFiles().length==0,"no partial or APK after rejection");
    }
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();
        check(InstallCache.apkName("Television.APK")&&!InstallCache.apkName("app.apks")&&!InstallCache.apkName("app.xapk")&&!InstallCache.apkName(null),"APK naming");
        Source good=new Source(3,new byte[]{97,98,99});File apk=InstallCache.copy(root,good,()->false);
        check(good.closed&&Arrays.equals(Files.readAllBytes(apk.toPath()),new byte[]{97,98,99}),"copied complete bytes and closed source");
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(InstallCache.sha256(apk)),"known SHA256");
        check(InstallCache.staged(root,"/"+apk.getName()).equals(apk),"exact file may be read");
        for(String path:new String[]{"/../else.apk","/a/../../else.apk","/%2e%2e/else.apk","/app.apk","/"+apk.getName()+"/x",null}){
            try{InstallCache.staged(root,path);throw new AssertionError("accepted unsafe path");}catch(IOException expected){checks++;}
        }
        apk.delete();
        rejected(root,new Source(InstallCache.MAX_BYTES+1,new byte[]{1}),false);
        rejected(root,new Source(8,new byte[]{1,2}),false);
        rejected(root,new Source(-1,new byte[0]),false);
        rejected(root,new Source(3,new byte[]{1,2,3}),true);
        Source offline=new Source(3,new byte[]{1,2,3});offline.fail=true;rejected(root,offline,false);
        Source unknown=new Source(-1,new byte[]{4,5});File complete=InstallCache.copy(root,unknown,()->false);check(complete.length()==2&&unknown.closed,"unknown-length provider handled");complete.delete();
        File outside=File.createTempFile("aperture-install-outside-",".apk");Path link=new File(root,"00000000-0000-0000-0000-000000000000.apk").toPath();Files.createSymbolicLink(link,outside.toPath());
        try{InstallCache.staged(root,"/"+link.getFileName());throw new AssertionError("accepted symlink escape");}catch(IOException expected){checks++;}finally{Files.delete(link);outside.delete();}
        System.out.println("Install cache: "+checks+" checks passed (limits, closure, incomplete/cancelled copy, hashes and URI traversal)");
    }
}

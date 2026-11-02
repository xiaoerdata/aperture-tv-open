package org.zhixiaoce.aperture;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.StatFs;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** App-private, bounded photographs. Originals and EXIF are discarded after normalization. */
final class GalleryStore {
    static final int MAX_UPLOAD=12*1024*1024,MAX_COUNT=300;static final long MAX_BYTES=512L*1024*1024;
    private static final Object LOCK=new Object();private final Context context;private final File directory;
    GalleryStore(Context context){this.context=context.getApplicationContext();directory=new File(this.context.getFilesDir(),"local-gallery");}
    static boolean id(String id){return id!=null&&id.matches("[a-f0-9]{32}");}
    private File[] files(){File[] f=directory.listFiles((d,n)->n.matches("[a-f0-9]{32}\\.jpg"));if(f==null)return new File[0];Arrays.sort(f,Comparator.comparingLong(File::lastModified).thenComparing(File::getName));return f;}
    JSONObject snapshot()throws Exception{synchronized(LOCK){JSONArray list=new JSONArray();long bytes=0;for(File f:files()){String id=f.getName().substring(0,32);bytes+=f.length();list.put(new JSONObject().put("id",id).put("src","/gallery/photos/"+id+".jpg").put("thumb","/gallery/thumbs/"+id+".jpg"));}android.content.SharedPreferences p=context.getSharedPreferences("local-gallery",0);return new JSONObject().put("photos",list).put("bytes",bytes).put("maxCount",MAX_COUNT).put("maxBytes",MAX_BYTES).put("template",p.getString("template","fade")).put("seconds",p.getInt("seconds",12));}}
    void option(String key,String value)throws IOException{if(key.equals("template")&&Arrays.asList("fade","drift","diptych").contains(value))context.getSharedPreferences("local-gallery",0).edit().putString(key,value).apply();else if(key.equals("seconds")&&Arrays.asList("8","12","20").contains(value))context.getSharedPreferences("local-gallery",0).edit().putInt(key,Integer.parseInt(value)).apply();else throw new IOException("设置无效");}
    void delete(String id)throws IOException{synchronized(LOCK){if(!id(id))throw new IOException("照片标识无效");File file=new File(directory,id+".jpg");if(file.exists()&&!file.delete())throw new IOException("照片未能删除");new File(new File(directory,"thumbs"),id+".jpg").delete();}}
    /** Native read-only viewer: never modifies or cleans an existing photograph. */
    Bitmap image(String id)throws IOException{
        if(!id(id))throw new IOException("照片不可读取");File file=new File(directory,id+".jpg");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),o);
        if(o.outWidth<=0||o.outHeight<=0||(long)o.outWidth*o.outHeight>48000000)throw new IOException("照片不可读取");
        o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1920)o.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),o);if(bitmap==null)throw new IOException("照片不可读取");return bitmap;
    }
    String add(byte[] bytes,BooleanSupplier authorized)throws Exception{
        if(!authorized.getAsBoolean())throw new IOException("上传已关闭，请重新扫码");
        if(bytes.length<16||bytes.length>MAX_UPLOAD)throw new IOException("单张照片上限12MB");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>48000000||!Arrays.asList("image/jpeg","image/png","image/webp").contains(bounds.outMimeType))throw new IOException("请选择JPEG、PNG或WebP照片，最大4800万像素");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2560)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("照片无法解码");Bitmap rotated=null,thumb=null;
        File temp=null,thumbnailOutput=null;boolean committed=false;try{
            int orientation=1;try{orientation=new ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
            rotated=matrix.isIdentity()?bitmap:Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
            synchronized(LOCK){
                if(!authorized.getAsBoolean())throw new IOException("上传已关闭，请重新扫码");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建相册");
                File[] current=files();long used=0;for(File f:current)used+=f.length();if(current.length>=MAX_COUNT||used>=MAX_BYTES||new StatFs(directory.getAbsolutePath()).getAvailableBytes()<128L*1024*1024)throw new IOException("相册空间不足，请先删除部分照片");
                String id=UUID.randomUUID().toString().replace("-","");temp=new File(directory,id+".part");try(FileOutputStream out=new FileOutputStream(temp)){if(!rotated.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("照片保存失败");out.getFD().sync();}
                if(used+temp.length()>MAX_BYTES||!authorized.getAsBoolean())throw new IOException("容量超限或上传已关闭");
                File thumbs=new File(directory,"thumbs");if(!thumbs.isDirectory()&&!thumbs.mkdirs())throw new IOException("无法创建缩略图");
                int w=Math.max(1,Math.round(rotated.getWidth()*Math.min(1f,480f/Math.max(rotated.getWidth(),rotated.getHeight())))),h=Math.max(1,Math.round(rotated.getHeight()*Math.min(1f,480f/Math.max(rotated.getWidth(),rotated.getHeight()))));thumb=Bitmap.createScaledBitmap(rotated,w,h,true);File thumbnail=new File(thumbs,id+".jpg");thumbnailOutput=thumbnail;try(FileOutputStream out=new FileOutputStream(thumbnail)){if(!thumb.compress(Bitmap.CompressFormat.JPEG,82,out))throw new IOException("缩略图保存失败");}
                if(!authorized.getAsBoolean()||!temp.renameTo(new File(directory,id+".jpg"))){thumbnail.delete();throw new IOException("上传已关闭或保存失败");}committed=true;return id;
            }
        }finally{if(!committed&&thumbnailOutput!=null)thumbnailOutput.delete();if(temp!=null)temp.delete();if(thumb!=null&&thumb!=rotated)thumb.recycle();if(rotated!=null&&rotated!=bitmap)rotated.recycle();bitmap.recycle();}
    }
    WebResourceResponse read(WebResourceRequest request){android.net.Uri uri=request.getUrl();if(!"GET".equals(request.getMethod())||!"https".equals(uri.getScheme())||!LobbyAssets.HOST.equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)return null;String path=uri.getPath();
        try{if("/api/gallery".equals(path))return response("application/json",new ByteArrayInputStream(snapshot().toString().getBytes("UTF-8")));
            if(path!=null&&path.matches("/gallery/(photos|thumbs)/[a-f0-9]{32}\\.jpg")){String id=path.substring(path.lastIndexOf('/')+1);File f=path.startsWith("/gallery/thumbs/")?new File(new File(directory,"thumbs"),id):new File(directory,id);return response("image/jpeg",new FileInputStream(f));}
        }catch(Exception unavailable){return new WebResourceResponse("text/plain","UTF-8",404,"Unavailable",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));}return null;
    }
    private WebResourceResponse response(String mime,InputStream in){return new WebResourceResponse(mime,mime.equals("application/json")?"UTF-8":null,200,"OK",Collections.singletonMap("Cache-Control","no-store"),in);}
}

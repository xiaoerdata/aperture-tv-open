package org.zhixiaoce.aperture;
import android.app.*;import android.os.Bundle;import android.content.*;import android.graphics.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;

/** Runs only on a task-owned emulator, signed by the existing candidate identity. Synthetic data only. */
public final class UpgradeRunner extends Instrumentation {
    private String phase;
    public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase");start();}
    private String sha(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder text=new StringBuilder();for(byte b:digest.digest())text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return text.toString();}
    private void save(Context c,String name,JSONObject value)throws Exception{try(FileOutputStream out=c.openFileOutput(name,0)){out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));}}
    private JSONObject read(Context c,String name)throws Exception{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),name).toPath()),StandardCharsets.UTF_8));}
    public void onStart(){JSONObject result=new JSONObject();try{
        Context c=getTargetContext();int version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;GalleryStore store=new GalleryStore(c);
        if("seed".equals(phase)){
            if(version!=36)throw new AssertionError("Seed requires real v36");
            c.getSharedPreferences("aperture",0).edit().putBoolean("simple_home",true).putBoolean("lite",true).putString("origin","https://upgrade-fixture.invalid").commit();
            store.option("template","diptych");store.option("seconds","20");Bitmap b=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888);b.eraseColor(0xff294a54);ByteArrayOutputStream stream=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,stream);b.recycle();String id=store.add(stream.toByteArray(),()->true);
            File photo=new File(c.getFilesDir(),"local-gallery/"+id+".jpg"),thumb=new File(c.getFilesDir(),"local-gallery/thumbs/"+id+".jpg");
            JSONObject snapshot=new JSONObject().put("photoId",id).put("photoSha256",sha(photo)).put("thumbSha256",sha(thumb)).put("seedVersionCode",36).put("syntheticOnly",true);save(c,"s94-upgrade-seed.json",snapshot);
            c.getSharedPreferences("kaishi_web_diagnostics",0).edit().putInt("versionCode",36).putString("reason","RENDERER_GONE").putLong("at",1111).commit();result.put("seeded",true).put("versionCode",version);
        }else{
            if(version!=37)throw new AssertionError("Verify requires real v37");JSONObject seed=read(c,"s94-upgrade-seed.json");String id=seed.getString("photoId");JSONObject snapshot=store.snapshot();
            if(!sha(new File(c.getFilesDir(),"local-gallery/"+id+".jpg")).equals(seed.getString("photoSha256"))||!sha(new File(c.getFilesDir(),"local-gallery/thumbs/"+id+".jpg")).equals(seed.getString("thumbSha256")))throw new AssertionError("Photo bytes changed");
            if(snapshot.getJSONArray("photos").length()!=1||!snapshot.getString("template").equals("diptych")||snapshot.getInt("seconds")!=20)throw new AssertionError("Album configuration changed");
            SharedPreferences p=c.getSharedPreferences("aperture",0);if(!p.getBoolean("simple_home",false)||!p.getBoolean("lite",false)||!"https://upgrade-fixture.invalid".equals(p.getString("origin","")))throw new AssertionError("Home configuration lost");
            new WebDiagnostics(c);SharedPreferences diagnostics=c.getSharedPreferences("kaishi_web_diagnostics",0);if(diagnostics.getInt("versionCode",0)!=37||diagnostics.contains("reason")||diagnostics.contains("at"))throw new AssertionError("Stale diagnostic retained after upgrade");
            result.put("versionCode",version).put("upgradedFrom",36).put("configurationPreserved",true).put("photoAndThumbnailBytesPreserved",true).put("oldDiagnosticsClearedOnly",true).put("realXiaomi",false).put("actualV36AndV37ProductApks",true).put("syntheticOnly",true);
        }
        result.put("ok",true);save(c,"s94-upgrade-"+phase+".json",result);
    }catch(Throwable e){try{result.put("ok",false).put("error",e.getClass().getSimpleName()+": "+e.getMessage());save(getTargetContext(),"s94-upgrade-"+phase+".json",result);}catch(Exception ignored){}}
        Bundle b=new Bundle();b.putString("result",result.toString());finish(result.optBoolean("ok")?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);
    }
}

package org.zhixiaoce.aperture;
import android.app.*;import android.os.Bundle;import org.json.*;
/** Teardown/report failures remain failures; instrumentation/shell return code is not acceptance. */
final class ProbeReport {
    interface Assemble {void add(JSONObject report)throws Exception;}
    private static void failure(JSONObject report,String field,Throwable error){try{report.put("ok",false).put(field,error.getClass().getSimpleName()+": "+error.getMessage());}catch(JSONException impossible){throw new AssertionError(impossible);}}
    static void finish(Instrumentation instrument,JSONObject result,String filename,Runnable teardown,Assemble assemble){
        try{teardown.run();}catch(Throwable error){failure(result,"teardownError",error);}
        try{assemble.add(result);}catch(Throwable error){failure(result,"assemblyError",error);}
        try(java.io.FileOutputStream file=instrument.getTargetContext().openFileOutput(filename,0)){file.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Throwable error){failure(result,"reportWriteError",error);}
        Bundle bundle=new Bundle();bundle.putString("result",result.toString());instrument.finish(result.optBoolean("ok")?Activity.RESULT_OK:Activity.RESULT_CANCELED,bundle);
    }
}

package org.zhixiaoce.aperture;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.io.IOException;
import org.json.JSONObject;

/** Decrypt, native validation, real directory test, then recheck authorization before local commit. */
final class NasSetupImport {
    static NasDraft verify(NasSetupCrypto crypto,NasSetupClient client,JSONObject delivery,BooleanSupplier active)throws Exception{
        if(!active.getAsBoolean())throw new IOException("交接已取消");
        NasDraft draft=new NasDraft(crypto.open(delivery.getJSONObject("envelope")));
        char[] password=draft.connection.getString("password").toCharArray();
        try(NasReader reader=NasReader.connect(new NasLocation(draft.connection.getString("root")),draft.connection.getString("user"),password,draft.connection.getString("domain"))){reader.list("");}
        finally{Arrays.fill(password,'\0');}
        if(!active.getAsBoolean())throw new IOException("交接已取消");
        JSONObject current=client.poll(crypto);
        if(!"delivered".equals(current.getString("state"))||current.getInt("revision")!=delivery.getInt("revision"))throw new IOException("配置授权已变化");
        if(!active.getAsBoolean())throw new IOException("交接已取消");
        return draft;
    }
}

package org.zhixiaoce.aperture;
import org.json.JSONObject;
/** NAS-specific adapter over the shared, authenticated fixed-path transport. */
final class NasSetupClient {
    private final HouseholdCommandClient client;
    NasSetupClient(String origin,String cookies,boolean debug)throws Exception{client=new HouseholdCommandClient(origin,cookies,debug);}
    JSONObject start(NasSetupCrypto crypto,String label)throws Exception{return client.call("nas-setup","start",crypto.hello(label));}
    JSONObject poll(NasSetupCrypto crypto)throws Exception{return client.call("nas-setup","poll",crypto.identify());}
    JSONObject finish(NasSetupCrypto crypto,String state,int revision)throws Exception{return client.call("nas-setup","finish",crypto.identify().put("state",state).put("revision",revision));}
    void interrupt(){client.interrupt();}
}

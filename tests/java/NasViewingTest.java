package org.zhixiaoce.aperture;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.*;

public final class NasViewingTest {
    static int count;
    interface Task { void run() throws Exception; }
    static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    static void reject(Task task,String message)throws Exception{boolean failed=false;try{task.run();}catch(Exception expected){failed=true;}check(failed,message);}
    static final class Memory implements CinemaShelfStore.Storage {
        String value="[]";boolean fail;
        public String read(){return value;}
        public void write(String json)throws Exception{if(fail)throw new IOException("full");value=json;}
    }
    public static void main(String[] args)throws Exception {
        NasLibrary library=new NasLibrary("[]");JSONObject connection=library.add(new JSONObject().put("name","家庭 NAS").put("root","smb://192.0.2.20/Family").put("user","fixture").put("password","synthetic-only-secret"));
        String id=connection.getString("id"),path="电影/周末/一家人的电影.mp4";
        JSONObject nas=NasWatchRoute.ref(id,path,"电影");
        check(!nas.toString().contains("192.0.2.20")&&!nas.toString().contains("synthetic-only-secret")&&!nas.toString().contains("fixture"),"NAS identity contains no server credentials");
        NasWatchRoute route=NasWatchRoute.resolve(nas,library);
        check(route.path.equals(path)&&route.folder.equals("电影/周末")&&route.root.equals("电影"),"reopen restores exact folder and original root");
        NasNavigation navigation=new NasNavigation();navigation.reset(route.root);check(navigation.contains(route.folder)&&!navigation.contains("相册"),"reopened browsing remains in selected library");
        check(NasWatchRoute.resolve(new JSONObject(nas.toString()).put("title","任意展示名"),library).path.equals(path),"display name cannot redirect file");
        for(String invalid:new String[]{"../secret.mp4","电影/../secret.mp4","/absolute.mp4","电影/%2e%2e/secret.mp4","https://example.invalid/a.mp4","电影\\secret.mp4"})reject(()->NasWatchRoute.ref(id,invalid,""),"unsafe path rejected "+invalid);
        reject(()->NasWatchRoute.ref(id,path,"相册"),"saved root cannot refer to sibling tree");reject(()->NasWatchRoute.ref("1",path,""),"noncanonical connection id rejected");reject(()->NasWatchRoute.ref(id,"电影/photo.jpg",""),"only playable NAS file kinds route into player");
        JSONObject second=library.add(new JSONObject(connection.toString()));JSONObject samePath=NasWatchRoute.ref(second.getString("id"),path,"");
        check(!CinemaShelf.key(nas).equals(CinemaShelf.key(samePath)),"same relative file in another connection stays distinct");
        library.remove(id);reject(()->NasWatchRoute.resolve(nas,library),"deleted connection fails without falling back to another NAS");
        String longName="a".repeat(230)+".mp4";JSONObject longRef=NasWatchRoute.ref(second.getString("id"),longName,"");CinemaShelf.key(longRef);check(longRef.getString("id").equals(longName)&&longRef.getString("title").length()==200,"long filename preserves ID with bounded title");

        Object lock=new Object();Memory memory=new Memory();CinemaShelfStore cinema=new CinemaShelfStore(lock,memory),nasStore=new CinemaShelfStore(lock,memory);
        JSONObject vod=CinemaShelf.ref("vod","https://example.invalid/api","episode-1","剧集","");
        CinemaShelf stale=cinema.read();
        nasStore.update(s->s.watch(nas,"NAS",nas.getString("title"),60000,120000,false,1000));
        cinema.update(s->s.toggle(vod,"favorite"));
        check(stale.list(null,"history").isEmpty()&&cinema.read().list(null,"history").size()==1,"stale Activity snapshot cannot overwrite NAS progress");
        check(cinema.read().get(vod).optBoolean("favorite")&&cinema.read().resume(nas,"NAS",nas.getString("title"))==60000,"cross-Activity favorite and resume survive sequential writes");
        nasStore.update(s->s.toggle(nas,"favorite"));cinema.update(s->s.watch(vod,"line","ep",30000,120000,false,1200));
        check(cinema.read().list(null,"history").size()==2&&cinema.read().list("nas","favorite").size()==1,"unified history includes cinema and NAS favorites");
        nasStore.update(s->s.watch(nas,"NAS",nas.getString("title"),120000,120000,true,1300));check(cinema.read().resume(nas,"NAS",nas.getString("title"))==0,"completed NAS starts again instead of seeking to end");
        nasStore.update(s->s.watch(nas,"NAS",nas.getString("title"),0,120000,false,1400));check(!cinema.read().get(nas).optBoolean("complete"),"start-over clears completion");
        cinema.update(CinemaShelf::clearHistory);check(cinema.read().list(null,"history").isEmpty()&&cinema.read().list("nas","favorite").size()==1,"unified history clear keeps NAS favorite");

        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);List<Future<?>> work=new ArrayList<>();
        for(int thread=0;thread<2;thread++){final int number=thread;work.add(pool.submit(()->{try{start.await();CinemaShelfStore writer=number==0?cinema:nasStore;for(int i=0;i<40;i++){JSONObject ref=CinemaShelf.ref("vod","thread-"+number,"id-"+i,"测试","");writer.update(s->s.toggle(ref,"favorite"));}}catch(Exception error){throw new RuntimeException(error);}}));}
        start.countDown();for(Future<?> task:work)task.get();pool.shutdown();check(cinema.read().list("vod","favorite").size()==81,"concurrent stores retain all independent writes");
        String before=memory.value;reject(()->cinema.update(s->{s.remove(nas);throw new IOException("cancel");}),"failed mutation rejects transaction");check(memory.value.equals(before),"failed mutation preserves disk state");
        memory.fail=true;reject(()->cinema.update(s->s.remove(nas)),"failed storage write rejects transaction");check(memory.value.equals(before),"failed commit retains previous value");memory.fail=false;
        memory.value="invalid";reject(()->cinema.update(s->s.toggle(vod,"favorite")),"corrupt state blocks writes");check(memory.value.equals("invalid"),"corrupt stored record is not replaced with empty state");memory.value=before;
        KeyGenerator keys=KeyGenerator.getInstance("AES");keys.init(256);SecretKey key=keys.generateKey();String encrypted=CinemaCipher.encrypt(memory.value,key);check(!encrypted.contains("一家人的电影")&&!encrypted.contains(path),"encrypted shared shelf does not expose local paths");
        CinemaShelf decrypted=new CinemaShelf(CinemaCipher.decrypt(encrypted,key));check(decrypted.list("nas","favorite").size()==1,"NAS favorite survives cipher and model roundtrip");
        System.out.println("NAS unified viewing: "+count+" assertions passed");
    }
}

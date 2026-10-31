package org.zhixiaoce.aperture;

import android.app.Activity;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Foreground-only, bounded remote reader + offline schedule. It never opens or brings an Activity forward. */
final class HomeMessages {
    private final Activity activity;private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private final CinemaHttp http=new CinemaHttp();private final Runnable changed;
    private volatile MessageFeed feed;private volatile boolean active,closed;private volatile int generation;private boolean fetching,ready,paused;private long lastPoll;private TextToSpeech tts;private AudioFocusRequest audioRequest;private final AudioManager audio;private JSONObject spoken=new JSONObject();
    HomeMessages(Activity a,Runnable changed){activity=a;this.changed=changed;prefs=a.getSharedPreferences("kaishi_messages",Context.MODE_PRIVATE);audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);paused=prefs.getBoolean("paused",false);try{feed=new MessageFeed(prefs.getString("cache",""));}catch(Exception ignored){}try{spoken=new JSONObject(prefs.getString("spoken","{}"));}catch(Exception ignored){}
        tts=new TextToSpeech(a,status->{main.post(()->{if(!closed&&status==TextToSpeech.SUCCESS){ready=tts.setLanguage(Locale.SIMPLIFIED_CHINESE)>=0;tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){main.post(()->releaseAudio());}public void onError(String id){main.post(()->releaseAudio());}});}});});
    }
    String source(){return prefs.getString("source","");}
    boolean paused(){return paused;}
    void paused(boolean value){paused=value;prefs.edit().putBoolean("paused",value).apply();if(value)stop();}
    void configure(String value)throws Exception {String next=MessageFeed.source(value);generation++;http.cancel();stop();feed=null;lastPoll=0;prefs.edit().putString("source",next).remove("cache").apply();changed.run();if(active)poll();}
    void active(boolean value){if(closed)return;active=value;main.removeCallbacks(tick);if(value){poll();main.post(tick);}else{generation++;http.cancel();stop();}}
    private final Runnable tick=new Runnable(){public void run(){if(!active||closed)return;long now=System.currentTimeMillis();if(now-lastPoll>=60000)poll();speakDue();main.postDelayed(this,1000);}};
    private void poll(){if(fetching||!active||source().isEmpty()||closed)return;fetching=true;lastPoll=System.currentTimeMillis();final int turn=generation;final String address=source();io.execute(()->{MessageFeed next=null;String json="";try{json=http.messages(address);next=new MessageFeed(json);}catch(Exception ignored){}final MessageFeed result=next;final String cache=json;main.post(()->{fetching=false;if(!closed&&active&&turn==generation&&address.equals(source())&&result!=null){feed=result;prefs.edit().putString("cache",cache).apply();changed.run();}if(!closed&&active&&turn!=generation){lastPoll=0;poll();}});});}
    byte[] display(){try{return (feed==null?new JSONObject().put("version",1).put("messages",new JSONArray()):feed.display(Instant.now())).put("configured",!source().isEmpty()).put("paused",paused).toString().getBytes(StandardCharsets.UTF_8);}catch(Exception failure){return "{\"messages\":[]}".getBytes(StandardCharsets.UTF_8);}}
    private void speakDue(){
        if(!active||closed||paused||!ready||feed==null||tts.isSpeaking()||audio.isMusicActive())return;
        Instant now=Instant.now();for(JSONObject m:feed.messages){String key=feed.due(m,now);if(key.isEmpty())continue;key=UUID.nameUUIDFromBytes(source().getBytes(StandardCharsets.UTF_8)).toString()+":"+key;if(spoken.has(key))continue;
            AudioFocusRequest request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(focus->{if(focus<0)stop();},main).build();
            releaseAudio();if(audio.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;audioRequest=request;
            try{spoken.put(key,System.currentTimeMillis());List<String> keys=new ArrayList<>();Iterator<String> it=spoken.keys();while(it.hasNext())keys.add(it.next());keys.sort((x,y)->Long.compare(spoken.optLong(x),spoken.optLong(y)));for(int i=0;i<keys.size()-512;i++)spoken.remove(keys.get(i));if(!prefs.edit().putString("spoken",spoken.toString()).commit()){releaseAudio();return;}}catch(Exception failure){releaseAudio();return;}
            if(tts.speak(m.optString("text"),TextToSpeech.QUEUE_FLUSH,null,key)==TextToSpeech.ERROR)releaseAudio();return;
        }
    }
    private void releaseAudio(){if(audioRequest!=null){audio.abandonAudioFocusRequest(audioRequest);audioRequest=null;}}
    private void stop(){if(tts!=null)tts.stop();releaseAudio();}
    void close(){closed=true;active=false;generation++;main.removeCallbacksAndMessages(null);http.cancel();stop();if(tts!=null)tts.shutdown();io.shutdownNow();}
}

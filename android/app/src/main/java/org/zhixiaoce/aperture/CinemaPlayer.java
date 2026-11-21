package org.zhixiaoce.aperture;

import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.*;
import okhttp3.OkHttpClient;

/** Network playback with bounded forward buffering/VOD segments and an OEM decoder fallback. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class CinemaPlayer {
    interface Prepared {void ready(CinemaPlayer player);}interface Completion {void done(CinemaPlayer player);}interface Error {boolean failed(CinemaPlayer player,int code,int detail);}
    interface Size {void changed(CinemaPlayer player,int width,int height);}interface Seek {void done(CinemaPlayer player);}interface Buffered {void changed(CinemaPlayer player,int percent);}
    interface Buffering {void changed(CinemaPlayer player,boolean buffering);}interface Fallback {void switching(CinemaPlayer player,long position);}
    static final class AudioTrack {final String name;final boolean selected;private final Runnable select;AudioTrack(String n,boolean selected,Runnable select){name=n;this.selected=selected;this.select=select;}void select(){select.run();}}
    private final Context context;private final boolean live,allowHttp;private final Handler main=new Handler(Looper.getMainLooper());private final String mime;
    private ExoPlayer exo;private MediaPlayer nativePlayer;private OkHttpClient network;private boolean released,ready,seeking,playRequested,nativeFallback;private int nativeBuffered;private long seekSerial;
    private Uri uri;private Map<String,String> headers=Collections.emptyMap();private SurfaceHolder surface;private AudioAttributes audio;
    private Prepared onPrepared;private Completion onCompletion;private Error onError;private Size onSize;private Seek onSeek;private Buffered onBuffered;private Buffering onBuffering;private Fallback onFallback;
    private final Runnable nativeTimeout=()->{if(!released&&nativePlayer!=null&&!ready&&playRequested)fail(0,0);};
    CinemaPlayer(Context context,boolean live,boolean allowHttp,String mime){this.context=context.getApplicationContext();this.live=live;this.allowHttp=allowHttp;this.mime=mime==null?"":mime;
        try{ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);int heap=manager==null?128:manager.getMemoryClass();CinemaStreamPolicy policy=new CinemaStreamPolicy(live,heap);
            DefaultLoadControl buffers=new DefaultLoadControl.Builder().setBufferDurationsMs(policy.minBufferMs,policy.maxBufferMs,policy.startMs,policy.rebufferMs).setTargetBufferBytes(policy.targetBytes).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(0,false).build();
            DataSource.Factory data=()->dataSource();DefaultMediaSourceFactory sources=new DefaultMediaSourceFactory(data).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(3));
            ExoPlayer engine=new ExoPlayer.Builder(this.context,new DefaultRenderersFactory(this.context).setEnableDecoderFallback(true)).setLoadControl(buffers).setMediaSourceFactory(sources).build();exo=engine;
            engine.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(released||exo!=engine)return;buffering(state==Player.STATE_BUFFERING);if(state==Player.STATE_READY){if(!ready){ready=true;if(onPrepared!=null)onPrepared.ready(CinemaPlayer.this);}if(engine.getPlaybackState()==Player.STATE_READY)finishSeek();}else if(state==Player.STATE_ENDED&&onCompletion!=null)onCompletion.done(CinemaPlayer.this);}
                @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(released||exo!=engine)return;if(reason==Player.DISCONTINUITY_REASON_SEEK)main.post(()->{if(exo==engine&&engine.getPlaybackState()==Player.STATE_READY)finishSeek();});}
                @Override public void onVideoSizeChanged(VideoSize size){if(!released&&exo==engine&&onSize!=null)onSize.changed(CinemaPlayer.this,Math.round(size.width*size.pixelWidthHeightRatio),size.height);}
                @Override public void onPlayerError(PlaybackException error){if(released||exo!=engine)return;if(!nativeFallback&&CinemaStreamPolicy.nativeFallback(error.errorCode)){fallback();return;}fail(error.errorCode,0);}
                @Override public void onEvents(Player player,Player.Events events){if(!released&&exo==engine&&onBuffered!=null)onBuffered.changed(CinemaPlayer.this,getBufferedPercentage());}
            });
        }catch(RuntimeException|LinkageError unsupported){nativeFallback=true;nativeEngine();}
    }
    private DataSource dataSource(){
        if(network==null)network=CinemaStreamHttp.client(uri.toString(),allowHttp);
        DataSource.Factory upstream=new OkHttpDataSource.Factory(network).setUserAgent("Kaishi-TV/0.2").setDefaultRequestProperties(headers);
        if(live)return upstream.createDataSource();
        try{SimpleCache cache=CinemaStreamCache.get(context);if(cache==null)return upstream.createDataSource();return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream).setCacheWriteDataSinkFactory(new CacheDataSink.Factory().setFragmentSize(2L*1024*1024)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).setCacheKeyFactory(spec->{try{Map<String,String> values=new LinkedHashMap<>(headers);values.putAll(spec.httpRequestHeaders);return CinemaStreamPolicy.cacheKey(spec.uri.toString(),values);}catch(Exception unavailable){throw new IllegalStateException("播放缓存编号无效");}}).createDataSource();}catch(RuntimeException unavailable){return upstream.createDataSource();}
    }
    void setAudioAttributes(AudioAttributes value){audio=value;if(exo!=null)exo.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(value.getUsage()).setContentType(value.getContentType()).setFlags(value.getFlags()).build(),false);if(nativePlayer!=null)nativePlayer.setAudioAttributes(value);}
    void setDisplay(SurfaceHolder value){surface=value;if(exo!=null)exo.setVideoSurfaceHolder(value);if(nativePlayer!=null)nativePlayer.setDisplay(value);}
    void setDataSource(Context ignored,Uri value,Map<String,String> headers)throws Exception{CinemaCatalog.url(value.toString(),null,allowHttp);uri=value;this.headers=new LinkedHashMap<>(headers);network=CinemaStreamHttp.client(value.toString(),allowHttp);if(nativePlayer!=null)nativePlayer.setDataSource(context,uri,this.headers);}
    void prepareAsync(){if(exo!=null){MediaItem.Builder item=new MediaItem.Builder().setUri(uri);if(!mime.isEmpty())item.setMimeType(mime);if(live)item.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(6000).setMinOffsetMs(3000).setMaxOffsetMs(15000).build());exo.setMediaItem(item.build());exo.prepare();}else if(nativePlayer!=null){main.postDelayed(nativeTimeout,25000);nativePlayer.prepareAsync();}}
    private void nativeEngine(){MediaPlayer p=new MediaPlayer();nativePlayer=p;if(audio!=null)p.setAudioAttributes(audio);if(surface!=null)p.setDisplay(surface);
        p.setOnPreparedListener(m->{if(released||nativePlayer!=m)return;main.removeCallbacks(nativeTimeout);ready=true;if(onPrepared!=null)onPrepared.ready(this);if(playRequested&&!seeking&&!m.isPlaying())m.start();});
        p.setOnSeekCompleteListener(m->{if(released||nativePlayer!=m)return;finishSeek();if(playRequested&&ready&&!m.isPlaying())m.start();});
        p.setOnBufferingUpdateListener((m,n)->{if(!released&&nativePlayer==m){nativeBuffered=n;if(onBuffered!=null)onBuffered.changed(this,n);}});
        p.setOnInfoListener((m,what,extra)->{if(!released&&nativePlayer==m&&(what==MediaPlayer.MEDIA_INFO_BUFFERING_START||what==MediaPlayer.MEDIA_INFO_BUFFERING_END))buffering(what==MediaPlayer.MEDIA_INFO_BUFFERING_START);return false;});
        p.setOnVideoSizeChangedListener((m,w,h)->{if(!released&&nativePlayer==m&&onSize!=null)onSize.changed(this,w,h);});p.setOnCompletionListener(m->{if(!released&&nativePlayer==m&&onCompletion!=null)onCompletion.done(this);});p.setOnErrorListener((m,a,b)->{if(!released&&nativePlayer==m)fail(a,b);return true;});
    }
    private void fallback(){nativeFallback=true;long position=getCurrentPosition();if(onFallback!=null)onFallback.switching(this,position);ready=false;seeking=false;seekSerial++;ExoPlayer previous=exo;exo=null;previous.release();try{nativeEngine();nativePlayer.setDataSource(context,uri,headers);main.postDelayed(nativeTimeout,25000);nativePlayer.prepareAsync();}catch(Exception unsupported){fail(0,0);}}
    private void buffering(boolean value){if(onBuffering!=null)onBuffering.changed(this,value);}
    private void fail(int code,int detail){main.removeCallbacks(nativeTimeout);if(onError!=null)onError.failed(this,code,detail);}
    void start(){if(released)return;playRequested=true;if(exo!=null)exo.play();else if(nativePlayer!=null&&ready&&!seeking)nativePlayer.start();}
    void pause(){playRequested=false;if(exo!=null)exo.pause();else if(nativePlayer!=null&&ready)nativePlayer.pause();}
    boolean isPlaying(){return exo!=null?exo.isPlaying():nativePlayer!=null&&ready&&nativePlayer.isPlaying();}
    long getCurrentPosition(){return exo!=null?Math.max(0,exo.getCurrentPosition()):nativePlayer!=null&&ready?nativePlayer.getCurrentPosition():0;}
    long getDuration(){return exo!=null?Math.max(0,exo.getDuration()):nativePlayer!=null&&ready?nativePlayer.getDuration():0;}
    int getBufferedPercentage(){return exo!=null?exo.getBufferedPercentage():nativeBuffered;}
    void seekTo(long position){seekTo(position,MediaPlayer.SEEK_CLOSEST);}
    void seekTo(long position,int mode){seeking=true;seekSerial++;if(exo!=null){exo.seekTo(Math.max(0,position));ExoPlayer engine=exo;long turn=seekSerial;main.post(()->{if(!released&&exo==engine&&turn==seekSerial&&engine.getPlaybackState()==Player.STATE_READY)finishSeek();});}else if(nativePlayer!=null)nativePlayer.seekTo(position,mode);}
    private void finishSeek(){if(!seeking)return;seeking=false;Seek listener=onSeek;if(listener!=null)listener.done(this);}
    void speed(float value){if(exo!=null)exo.setPlaybackParameters(new PlaybackParameters(value));else if(nativePlayer!=null){boolean paused=!nativePlayer.isPlaying();nativePlayer.setPlaybackParams(nativePlayer.getPlaybackParams().setSpeed(value));if(paused)nativePlayer.pause();}}
    List<AudioTrack> audioTracks(){List<AudioTrack> result=new ArrayList<>();if(exo!=null){ExoPlayer engine=exo;for(Tracks.Group group:engine.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){final int at=i;Format format=group.getTrackFormat(i);String name=format.label!=null?format.label:format.language==null?"默认":format.language;result.add(new AudioTrack(name,group.isTrackSelected(i),()->{if(!released&&exo==engine)engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),at)).build());}));}}else if(nativePlayer!=null&&ready){MediaPlayer engine=nativePlayer;MediaPlayer.TrackInfo[] tracks=engine.getTrackInfo();for(int i=0;i<tracks.length;i++)if(tracks[i].getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO){final int at=i;result.add(new AudioTrack(tracks[i].getLanguage(),false,()->{if(!released&&nativePlayer==engine)engine.selectTrack(at);}));}}return result;}
    void setOnPreparedListener(Prepared listener){onPrepared=listener;}void setOnCompletionListener(Completion listener){onCompletion=listener;}void setOnErrorListener(Error listener){onError=listener;}void setOnVideoSizeChangedListener(Size listener){onSize=listener;}void setOnSeekCompleteListener(Seek listener){onSeek=listener;}void setOnBufferingUpdateListener(Buffered listener){onBuffered=listener;}void setOnBufferingListener(Buffering listener){onBuffering=listener;}void setOnFallbackListener(Fallback listener){onFallback=listener;}
    void release(){if(released)return;released=true;playRequested=false;main.removeCallbacksAndMessages(null);if(exo!=null){ExoPlayer engine=exo;exo=null;engine.release();}if(nativePlayer!=null){nativePlayer.release();nativePlayer=null;}if(network!=null)network.connectionPool().evictAll();}
}

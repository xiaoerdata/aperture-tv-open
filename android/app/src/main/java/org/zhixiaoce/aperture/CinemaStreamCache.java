package org.zhixiaoce.aperture;

import android.content.Context;
import java.io.File;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.*;

/** Private, process-wide VOD segment cache; requested on a loader thread, never the UI thread. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class CinemaStreamCache {
    private static SimpleCache cache;
    static synchronized SimpleCache get(Context context){
        if(cache!=null)return cache;File directory=new File(context.getCacheDir(),"cinema-stream-v1");long budget=CinemaStreamPolicy.diskBudget(context.getCacheDir().getUsableSpace());if(budget==0)return null;
        cache=new SimpleCache(directory,new LeastRecentlyUsedCacheEvictor(budget),new StandaloneDatabaseProvider(context.getApplicationContext()));return cache;
    }
}

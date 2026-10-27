package org.zhixiaoce.aperture;

/** Millisecond navigation shared by native player controls, independent of decoder and Android UI. */
final class PlaybackTimeline {
    static long step(long position,long duration,int direction,int repeat){long amount=repeat>=12?40000:repeat>=5?20000:10000;return Math.max(0,Math.min(Math.max(0,duration),position+direction*amount));}
    static String time(long millis){long seconds=Math.max(0,millis)/1000;return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
}

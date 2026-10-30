package org.zhixiaoce.aperture;
import android.service.dreams.DreamService;
/** Optional Android system dream. OEM settings decide whether/when it may run. */
public final class AmbientDreamService extends DreamService {
    private AmbientWeb page;
    public void onAttachedToWindow(){super.onAttachedToWindow();setInteractive(false);setFullscreen(true);setScreenBright(false);page=new AmbientWeb(this,"dream",this::finish);setContentView(page.web);}
    public void onDreamingStarted(){super.onDreamingStarted();if(page!=null)page.active(true);}
    public void onDreamingStopped(){if(page!=null)page.active(false);super.onDreamingStopped();}
    public void onDetachedFromWindow(){if(page!=null){page.close();page=null;}super.onDetachedFromWindow();}
}

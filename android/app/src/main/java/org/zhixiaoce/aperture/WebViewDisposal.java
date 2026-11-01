package org.zhixiaoce.aperture;

import android.view.ViewGroup;
import android.webkit.WebView;

/** A gone renderer cannot be stopped, paused or evaluated. Detach it before destruction. */
final class WebViewDisposal {
    private WebViewDisposal(){}
    static void release(WebView view,boolean rendererGone){
        if(view==null)return;
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        if(!rendererGone)try{view.stopLoading();}catch(RuntimeException ignored){}
        try{view.destroy();}catch(RuntimeException ignored){}
    }
}

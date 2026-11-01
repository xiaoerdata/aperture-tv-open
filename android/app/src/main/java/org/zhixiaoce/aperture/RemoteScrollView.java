package org.zhixiaoce.aperture;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;

/** Oversized focused rows scroll in place before direction keys move to another control. */
final class RemoteScrollView extends ScrollView {
    RemoteScrollView(Context context){super(context);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if((key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)&&event.getAction()==KeyEvent.ACTION_DOWN){
            View focus=findFocus();
            if(focus!=null&&focus!=this&&focus.getParent()!=null){
                Rect rect=new Rect(0,0,focus.getWidth(),focus.getHeight());offsetDescendantRectToMyCoords(focus,rect);
                int top=getScrollY()+getPaddingTop(),bottom=getScrollY()+getHeight()-getPaddingBottom();
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&rect.bottom>bottom+1){scrollBy(0,Math.min(rect.bottom-bottom,Math.max(1,Math.round((bottom-top)*.65f))));return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP&&rect.top<top-1){scrollBy(0,-Math.min(top-rect.top,Math.max(1,Math.round((bottom-top)*.65f))));return true;}
            }
        }
        return super.dispatchKeyEvent(event);
    }
}

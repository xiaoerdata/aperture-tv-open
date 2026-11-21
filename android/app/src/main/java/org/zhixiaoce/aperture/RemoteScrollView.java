package org.zhixiaoce.aperture;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;

/** Oversized focused rows scroll in place before direction keys move to another control. */
final class RemoteScrollView extends ScrollView {
    private final boolean accelerate;
    RemoteScrollView(Context context){this(context,false);}
    RemoteScrollView(Context context,boolean accelerate){super(context);this.accelerate=accelerate;}
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
        if(accelerate&&key==KeyEvent.KEYCODE_DPAD_DOWN&&event.getAction()==KeyEvent.ACTION_DOWN){
            int steps=SettingsScroll.steps(event.getRepeatCount(),event.getEventTime()-event.getDownTime());
            if(steps>1){View focus=findFocus(),target=focus;
                if(focus!=null&&focus!=this){for(int i=0;i<steps;i++){
                    View next=target.focusSearch(View.FOCUS_DOWN);if(next==null||!inside(next)||!next.isEnabled())break;target=next;
                }
                if(target!=focus){target.requestFocus();target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true);}return true;}
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean inside(View view){android.view.ViewParent p=view.getParent();while(p instanceof View){if(p==this)return true;p=p.getParent();}return false;}
}

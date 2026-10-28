package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;

/** Transparent transport controls; vector paths stay sharp at television resolutions. */
final class PlayerChrome {
    static Button control(Activity a,String label,Runnable action){
        Button b=new Button(a);b.setAllCaps(false);b.setText(label);b.setTextSize(BuildConfig.PARENTS_EDITION?20:18);b.setTextColor(TvUi.INK);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(TvUi.dp(a,52));b.setMinimumHeight(TvUi.dp(a,52));b.setPadding(TvUi.dp(a,16),0,TvUi.dp(a,16),0);b.setGravity(Gravity.CENTER);
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},surface(a,true));states.addState(new int[]{android.R.attr.state_pressed},surface(a,true));states.addState(new int[]{},surface(a,false));b.setBackground(states);b.setOnClickListener(v->action.run());return b;
    }
    private static Drawable surface(Activity a,boolean focused){GradientDrawable d=new GradientDrawable();d.setColor(focused?0x30ffffff:0x00000000);d.setCornerRadius(TvUi.dp(a,26));if(focused)d.setStroke(TvUi.dp(a,2),0xfff4f2ed);return d;}
    static void transport(Activity a,Button b,boolean playing){
        b.setText("");b.setContentDescription(playing?"暂停":"播放");Drawable icon=new TransportIcon(playing);icon.setBounds(0,0,TvUi.dp(a,24),TvUi.dp(a,24));b.setCompoundDrawables(icon,null,null,null);
    }
    private static final class TransportIcon extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean pause;
        TransportIcon(boolean value){pause=value;paint.setColor(0xfff4f2ed);}
        @Override public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);if(pause){c.drawRoundRect(5,3,10,21,1,1,paint);c.drawRoundRect(14,3,19,21,1,1,paint);}else{Path p=new Path();p.moveTo(6,3);p.lineTo(21,12);p.lineTo(6,21);p.close();c.drawPath(p,paint);}c.restore();}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

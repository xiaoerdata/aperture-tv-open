package org.zhixiaoce.aperture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Deterministic title art is a fallback, never a fabricated film poster. */
final class CinemaPoster extends FrameLayout {
    final ImageView image;
    CinemaPoster(Activity activity,String title){super(activity);float hue=(title.hashCode()&0x7fffffff)%360;GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.HSVToColor(new float[]{hue,.38f,.33f}),Color.HSVToColor(new float[]{hue,.45f,.12f})});shape.setCornerRadius(Math.max(TvUi.dp(activity,8),activity.getResources().getDisplayMetrics().widthPixels*.0094f));setBackground(shape);setClipToOutline(true);TextView letters=new TextView(activity);int count=Math.min(2,title.codePointCount(0,title.length()));letters.setText(title.substring(0,title.offsetByCodePoints(0,count)));letters.setTextColor(0xfff4eee1);letters.setTextSize(40);letters.setTypeface(Typeface.DEFAULT,Typeface.BOLD);letters.setGravity(Gravity.CENTER);letters.setContentDescription(title+" · 暂无海报");addView(letters,new LayoutParams(-1,-1));image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(image,new LayoutParams(-1,-1));}
}

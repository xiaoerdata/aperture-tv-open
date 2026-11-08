package org.zhixiaoce.aperture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/** Local vector QR renderer, including a four-module quiet zone. No WebView or network. */
final class PhoneQrView extends View {
    private final QrCode code;
    private final Paint paint=new Paint();
    PhoneQrView(Context context,String address){
        super(context);code=QrCode.encodeText(address,QrCode.Ecc.MEDIUM);
        setContentDescription("手机扫码连接电视");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBackgroundColor(Color.WHITE);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int cell=Math.min(getWidth(),getHeight())/(code.size+8);
        if(cell<1)return;
        int left=(getWidth()-cell*code.size)/2,top=(getHeight()-cell*code.size)/2;
        paint.setColor(Color.BLACK);
        for(int y=0;y<code.size;y++)for(int x=0;x<code.size;x++)if(code.getModule(x,y))
            canvas.drawRect(left+x*cell,top+y*cell,left+(x+1)*cell,top+(y+1)*cell,paint);
    }
}

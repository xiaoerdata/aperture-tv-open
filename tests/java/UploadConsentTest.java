package org.zhixiaoce.aperture;
public final class UploadConsentTest {
    static int n;static void ok(boolean v,String name){if(!v)throw new AssertionError(name);n++;}
    public static void main(String[] args){
        UploadConsent g=new UploadConsent();Object page=new Object();UploadConsent.Ticket t=g.request(page,100);
        ok(g.pending(t)&&!g.accepted(t),"request is not consent");ok(!g.up(t,99),"old page key up ignored");g.down(t,99,0);ok(!g.up(t,99),"old page key down/up ignored");
        g.down(t,101,0);g.down(t,101,0);ok(g.up(t,101),"one fresh released gesture accepted");ok(!g.up(t,101),"repeat key up cannot accept twice");ok(g.accepted(t),"accepted current ticket");
        g.cancel();ok(!g.accepted(t)&&!g.pending(),"cancel invalidates accepted callback");t=g.request(page,200);g.down(t,201,0);g.down(t,201,1);ok(!g.up(t,201),"long press repeat disarms");g.down(t,201,0);ok(!g.up(t,201),"same repeated gesture cannot rearm");g.down(t,202,0);ok(g.up(t,202),"new short gesture can consent");
        UploadConsent.Ticket old=t;t=g.request(new Object(),300);ok(!g.explicitClick(old,400)&&!g.accepted(old),"old dialog click rejected after page change");ok(!g.explicitClick(t,300),"same opening event rejected");g.cancel();ok(!g.explicitClick(t,400),"late callback after cancel rejected");
        t=g.request(page,500);g.down(t,501,0);g.disarm(t);ok(!g.up(t,501),"touch cancellation disarms");g.down(t,502,0);ok(!g.up(t,503)&&!g.up(t,502),"mismatched release cannot be followed by stale release");
        g.cancel();t=g.request(page,600);ok(g.explicitClick(t,601),"native explicit click allowed once");ok(!g.explicitClick(t,602),"duplicated click not two sessions");g.cancel();ok(!g.accepted(t),"background or destroy cancels async acceptance");
        System.out.println("UploadConsent: "+n+" checks passed; no launcher or network instantiated");
    }
}

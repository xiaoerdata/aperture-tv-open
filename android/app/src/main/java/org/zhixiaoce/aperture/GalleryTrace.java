package org.zhixiaoce.aperture;

import java.util.ArrayList;
import java.util.List;

/** Fixed vocabulary only: the trace cannot accept URLs, filenames, photos or console strings. */
final class GalleryTrace {
    enum Stage { CREATE, ROUTE_WEB, ROUTE_NATIVE, RESUME, PAUSE, FOCUS, DESTROY, DOCUMENT_START, DOCUMENT_FINISH, DOCUMENT_COMMIT, RESOURCE_HIT, RESOURCE_ERROR, RESOURCE_DENIED, API_OK, API_ERROR, READY_REQUEST, READY_CALLBACK, READY_ERROR, READY_VISIBLE, DEADLINE_START, DEADLINE_ARM, DEADLINE_PAUSE, DEADLINE_CANCEL, DEADLINE_EXPIRE, STALE_CALLBACK, FALLBACK, NATIVE_READY, NATIVE_SNAPSHOT_OK, NATIVE_SNAPSHOT_ERROR, CONSOLE, FINISH_REQUESTED, UPLOAD_CREATED, UPLOAD_QUEUED, UPLOAD_START_ENTERED, UPLOAD_START_REJECTED, UPLOAD_UI_QUEUED, UPLOAD_UI_REJECTED, UPLOAD_WEB_COMMITTED, UPLOAD_NATIVE_COMMITTED, UPLOAD_UI_FAILED, UPLOAD_REVOKED, UPLOAD_REFRESH_REJECTED }
    enum Asset { NONE, DOCUMENT, STYLE, GALLERY_SCRIPT, SETTINGS_SCRIPT, QR_SCRIPT, API, PHOTO, OTHER }
    enum Result { NONE, TRUE, FALSE, UNKNOWN, SYNTAX_ERROR, REFERENCE_ERROR, TYPE_ERROR, OTHER_ERROR }
    static final int LIMIT=160;
    static final class Entry {
        final Stage stage;final Asset asset;final Result result;final long generation,uptime,remaining,dueAt,delay,callbackDueAt,callbackAt;final boolean resumed,focused,active,matched,armed,budgetObserved;final int line;
        Entry(Stage s,Asset a,Result r,long g,long time,PageDeadline.Snapshot budget,boolean resumed,boolean focused,boolean matched,int line){stage=s;asset=a;result=r;generation=g;uptime=time;remaining=budget==null?-1:budget.remaining;dueAt=budget==null?-1:budget.dueAt;delay=budget==null?0:budget.delay;callbackDueAt=budget==null?-1:budget.callbackDueAt;callbackAt=budget==null?-1:budget.callbackAt;budgetObserved=budget!=null;active=budget==null?resumed:budget.active;armed=budget!=null&&budget.armed;this.resumed=resumed;this.focused=focused;this.matched=matched;this.line=Math.max(0,Math.min(1000000,line));}
    }
    private final List<Entry> entries=new ArrayList<>();private final int[][] counts=new int[Stage.values().length][Asset.values().length];
    synchronized boolean add(Entry e){
        // Asset success spam (including photos) cannot evict all lifecycle/error evidence.
        if((e.stage==Stage.RESOURCE_HIT||e.stage==Stage.API_OK)&&counts[e.stage.ordinal()][e.asset.ordinal()]++>=3)return false;
        if(entries.size()==LIMIT)entries.remove(0);entries.add(e);return true;
    }
    synchronized List<Entry> snapshot(){return new ArrayList<>(entries);}
    static Asset asset(String path){
        if(path==null)return Asset.OTHER;
        switch(path){case "/assets/gallery.html":return Asset.DOCUMENT;case "/assets/gallery.css":return Asset.STYLE;case "/assets/gallery.js":return Asset.GALLERY_SCRIPT;case "/assets/gallery-settings.js":return Asset.SETTINGS_SCRIPT;case "/assets/gallery-qr.js":return Asset.QR_SCRIPT;case "/api/gallery":return Asset.API;default:return path.startsWith("/gallery/photos/")||path.startsWith("/gallery/thumbs/")?Asset.PHOTO:Asset.OTHER;}
    }
    static Result console(String message){
        if(message==null)return Result.OTHER_ERROR;
        if(message.startsWith("Uncaught SyntaxError")||message.startsWith("SyntaxError"))return Result.SYNTAX_ERROR;
        if(message.startsWith("Uncaught ReferenceError")||message.startsWith("ReferenceError"))return Result.REFERENCE_ERROR;
        if(message.startsWith("Uncaught TypeError")||message.startsWith("TypeError"))return Result.TYPE_ERROR;
        return Result.OTHER_ERROR;
    }
}

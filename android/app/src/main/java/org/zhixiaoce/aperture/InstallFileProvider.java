package org.zhixiaoce.aperture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Private provider: only the single explicitly granted staged APK can be read. */
public final class InstallFileProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        try {
            if (!"content".equals(uri.getScheme()) || !(getContext().getPackageName()+".install").equals(uri.getAuthority()) || uri.getQuery()!=null || uri.getFragment()!=null) throw new FileNotFoundException();
            return InstallCache.staged(InstallCache.directory(getContext().getCacheDir()), uri.getPath());
        } catch (IOException error) { throw new FileNotFoundException(); }
    }
    public String getType(Uri uri) { try { file(uri); return ApkInstaller.MIME; } catch (FileNotFoundException error) { return null; } }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file=file(uri); String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns); Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++) values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"application.apk":OpenableColumns.SIZE.equals(columns[i])?file.length():null;
            cursor.addRow(values); return cursor;
        } catch(FileNotFoundException error) { return null; }
    }
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}

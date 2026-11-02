package org.zhixiaoce.aperture;
/** Real provider selection and real native/store UI, but never a real upload session. */
public class R3NativeGalleryProbe extends GalleryActivity {
    volatile int uploadCreates;
    @Override protected GalleryUploadSession createUploadSession(Runnable changed){uploadCreates++;throw new AssertionError("Real UI probe must never accept upload");}
}

package org.zhixiaoce.aperture;

/** A key-up belongs only to its original key-down and screen. No Android dependencies. */
final class ConfirmPress {
    private int key = -1, screen;
    private long downTime;
    void down(int code, long time, int generation) { key = code; downTime = time; screen = generation; }
    boolean up(int code, long time, int generation, boolean canceled) {
        boolean valid = key == code && downTime == time && screen == generation && !canceled;
        cancel();
        return valid;
    }
    void cancel() { key = -1; }
}

package org.zhixiaoce.aperture;

/** One foreground-entry intent; an interruption or explicit pause revokes it. */
final class PlaybackEntryIntent {
    private boolean pending;
    void begin(boolean alreadyActive){pending=!alreadyActive;}
    boolean pending(){return pending;}
    boolean take(boolean active){if(!pending||!active)return false;pending=false;return true;}
    void cancel(){pending=false;}
}

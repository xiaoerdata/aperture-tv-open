package org.zhixiaoce.aperture;

/** Only held Down accelerates. Android supplies fresh repeat/time values per physical press. */
final class SettingsScroll {
    private SettingsScroll(){}
    static int steps(int repeat,long heldMs){
        if(repeat<=0)return 1;
        if(heldMs>=1800||repeat>=18)return 4;
        return heldMs>=700||repeat>=6?2:1;
    }
}

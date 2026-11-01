package org.zhixiaoce.aperture;

/** Small state machine shared by the real service and deterministic transition checks. */
final class HomeGuardPolicy {
    private boolean visiting;
    private long visitAt = Long.MIN_VALUE / 2;
    private long lastLaunch = Long.MIN_VALUE / 2;
    HomeGuardPolicy(boolean visiting) { this.visiting = visiting; }
    void visit(long now) { visiting = true; visitAt = now; }
    void resumeHome() { visiting = false; }
    boolean visiting() { return visiting; }
    boolean shouldOpen(boolean enabled, boolean interactive, boolean stockHome, boolean homeKey, long now) {
        if (!enabled || !interactive || (!stockHome && !homeKey)) return false;
        // An implicit HOME used to leave Kaishi can itself produce a homekey broadcast.
        if (visiting && (!homeKey || now - visitAt < 1500)) return false;
        if (now - lastLaunch < 1200) return false;
        visiting = false; lastLaunch = now; return true;
    }
}

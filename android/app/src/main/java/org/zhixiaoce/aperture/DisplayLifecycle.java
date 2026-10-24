package org.zhixiaoce.aperture;

/** A system assistant can take window focus without pausing the Activity. */
final class DisplayLifecycle {
    interface Target { void setActive(boolean active); }
    private boolean resumed, focused;
    private Boolean applied;
    private Target target;
    private boolean lastActive;
    private long interruptions;
    void bind(Target next) { target = next; applied = null; sync(); }
    void resumed(boolean value) { resumed = value; sync(); }
    void focused(boolean value) { focused = value; sync(); }
    boolean active() { return resumed && focused; }
    long interruptions() { return interruptions; }
    private void sync() {
        boolean next = active();
        if (lastActive && !next) interruptions++;
        lastActive = next;
        if (target != null && (applied == null || applied != next)) { applied = next; target.setActive(next); }
    }
}

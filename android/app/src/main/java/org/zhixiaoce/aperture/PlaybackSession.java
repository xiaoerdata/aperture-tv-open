package org.zhixiaoce.aperture;

/** Main-thread playback intent. Audio/window interruption never grants a new play intent. */
final class PlaybackSession {
    interface Focus {
        boolean request(long token, Runnable lost);
        void abandon(long token);
    }
    interface Output {
        void start();
        void pause();
        void changed(State state);
    }
    enum State { IDLE, PREPARING, PLAYING, PAUSED, INTERRUPTED, FOCUS_DENIED, COMPLETED, ERROR }
    private final Focus focus;
    private final Output output;
    private long serial, token;
    private boolean active, surface, open, ready, wantsPlay, granted;
    private State state = State.IDLE;

    PlaybackSession(Focus focus, Output output) { this.focus = focus; this.output = output; }
    State state() { return state; }
    void active(boolean value) {
        active = value;
        if (!value && open && state != State.ERROR && state != State.COMPLETED) stop(State.INTERRUPTED);
    }
    void surface(boolean value) {
        surface = value;
        if (!value && open && state != State.ERROR && state != State.COMPLETED) stop(State.INTERRUPTED);
    }
    void begin() {
        reset(); open = true; state = State.PREPARING;
        // Reserve only for a foreground user selection, including its asynchronous preparation.
        if (active) { wantsPlay = true; acquire(); }
        else state = State.INTERRUPTED;
        output.changed(state);
    }
    void prepared() { if (open && state != State.ERROR) { ready = true; maybeStart(); output.changed(state); } }
    void play() {
        if (!open || !ready || !active || !surface || state == State.ERROR) return;
        if (state == State.PLAYING) return;
        wantsPlay = true; acquire(); maybeStart(); output.changed(state);
    }
    void pause() { if (open && state != State.ERROR) stop(State.PAUSED); }
    void completed() { if (open && state != State.ERROR) stop(State.COMPLETED); }
    void error() { if (open) { ready = false; stop(State.ERROR); } }
    void reset() {
        wantsPlay = false; dropFocus();
        pauseOutput(); open = false; ready = false; surface = false; state = State.IDLE;
    }
    private void acquire() {
        dropFocus();
        final long request = ++serial; token = request;
        boolean result;
        try { result = focus.request(request, () -> lost(request)); }
        catch (RuntimeException denied) { result = false; }
        // A synchronous loss or reset during request also cancels its return value.
        if (token != request) return;
        if (result) granted = true;
        else { wantsPlay = false; dropFocus(); state = State.FOCUS_DENIED; }
    }
    private void lost(long request) {
        if (token == request && open) stop(State.INTERRUPTED);
    }
    private void maybeStart() {
        if (!open || !ready || !active || !surface || !wantsPlay || !granted || token == 0) return;
        try { output.start(); wantsPlay = false; state = State.PLAYING; }
        catch (RuntimeException failed) { error(); }
    }
    private void stop(State next) {
        wantsPlay = false; dropFocus(); pauseOutput(); state = next; output.changed(state);
    }
    private void pauseOutput() { try { output.pause(); } catch (RuntimeException ignored) { /* Error/release can invalidate MediaPlayer. */ } }
    private void dropFocus() {
        long old = token; token = 0; granted = false;
        if (old != 0) try { focus.abandon(old); } catch (RuntimeException ignored) { /* Never retain play intent after platform failure. */ }
    }
}

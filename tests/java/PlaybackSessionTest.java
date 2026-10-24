package org.zhixiaoce.aperture;

import java.util.*;

/** Real production intent controller with synthetic audio/player ports; not Android execution. */
public final class PlaybackSessionTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static final class Rig implements PlaybackSession.Focus, PlaybackSession.Output {
        final PlaybackSession session = new PlaybackSession(this, this);
        final Map<Long, Runnable> callbacks = new HashMap<>();
        final List<String> timeline = new ArrayList<>();
        long held, last; int requests, starts, pauses, abandons;
        boolean allow = true, immediateLoss, failStart, failRequest, failAbandon, playing;
        public boolean request(long token, Runnable lost) {
            requests++; last = token; held = token; callbacks.put(token, lost);
            if (failRequest) throw new IllegalStateException("synthetic platform failure");
            if (immediateLoss) lost.run();
            return allow;
        }
        public void abandon(long token) {
            check(token == held, "never abandon a replacement request"); abandons++; held = 0;
            if (failAbandon) throw new IllegalStateException("synthetic abandon failure");
        }
        public void start() {
            if (failStart) throw new IllegalStateException("synthetic decoder failure");
            starts++; playing = true;
        }
        public void pause() { if (playing) pauses++; playing = false; }
        public void changed(PlaybackSession.State state) { timeline.add(state.name()); }
        void ready() { session.active(true); session.begin(); session.surface(true); session.prepared(); }
        void loss() { callbacks.get(last).run(); }
        void status(PlaybackSession.State state) { check(session.state() == state, "expected " + state + " got " + session.state()); }
        void quiet() { check(!playing && held == 0, "silent and no focus held"); }
    }
    public static void main(String[] args) {
        Rig normal = new Rig(); normal.ready(); normal.status(PlaybackSession.State.PLAYING);
        check(normal.starts == 1 && normal.requests == 1, "one foreground selection, one start");
        normal.session.prepared(); check(normal.starts == 1, "duplicate prepared event cannot start twice");
        normal.session.pause(); normal.quiet(); normal.session.active(false); normal.session.active(true);
        check(normal.starts == 1, "visibility is not play intent"); normal.session.play(); check(normal.starts == 2 && normal.requests == 2, "explicit resume reacquires");
        normal.session.completed(); normal.quiet(); normal.status(PlaybackSession.State.COMPLETED);
        normal.session.play(); check(normal.starts == 3, "explicit replay after completion"); normal.session.reset(); normal.quiet();
        System.out.println("PASS foreground selection / user pause / explicit resume / completion / replay / reset");

        Rig preparing = new Rig(); preparing.session.active(true); preparing.session.begin(); preparing.loss();
        preparing.session.surface(true); preparing.session.prepared(); preparing.quiet(); preparing.status(PlaybackSession.State.INTERRUPTED);
        check(preparing.starts == 0, "audio-only loss during preparation cancels autoplay");
        preparing.session.play(); check(preparing.starts == 1, "manual continue after prepare");
        System.out.println("PASS audio-only loss during preparation / no late autoplay / explicit continue");

        Rig running = new Rig(); running.ready(); running.loss(); running.quiet(); check(running.pauses == 1, "audio-only loss pauses immediately");
        running.session.prepared(); running.session.active(true); check(running.starts == 1, "duplicate prepared or focus return cannot resume");
        System.out.println("PASS audio-only loss during playback / repeated prepared and active events stay paused");

        Rig denied = new Rig(); denied.allow = false; denied.ready(); denied.quiet(); denied.status(PlaybackSession.State.FOCUS_DENIED);
        check(denied.starts == 0, "focus denial never plays"); denied.allow = true;
        check(denied.starts == 0, "availability alone is not play intent"); denied.session.play(); check(denied.starts == 1, "explicit retry after denial");
        System.out.println("PASS denied focus / no delayed playback / user retry");

        Rig synchronous = new Rig(); synchronous.immediateLoss = true; synchronous.ready(); synchronous.quiet();
        check(synchronous.starts == 0, "granted return must not override synchronous loss"); synchronous.status(PlaybackSession.State.INTERRUPTED);
        System.out.println("PASS loss callback before request returns / late grant is ignored");

        Rig stale = new Rig(); stale.ready(); Runnable old = stale.callbacks.get(stale.last); stale.session.reset(); stale.ready();
        long replacement = stale.held; old.run(); check(stale.playing && stale.held == replacement && stale.starts == 2, "old source callback cannot stop replacement");
        stale.session.pause(); Runnable pausedCallback = stale.callbacks.get(stale.last); stale.session.play(); pausedCallback.run();
        check(stale.playing && stale.starts == 3, "old request cannot stop explicit new play");
        stale.session.reset(); stale.session.prepared(); old.run(); stale.quiet(); check(stale.starts == 3, "late events after stop cannot play");
        System.out.println("PASS stale source and focus callbacks / replacement stays playing / late events after reset");

        Rig window = new Rig(); window.session.active(true); window.session.begin(); window.session.active(false); window.session.active(true); window.session.surface(true); window.session.prepared(); window.quiet();
        check(window.starts == 0, "window interruption while preparing cancels autoplay"); window.session.play();
        window.session.surface(false); window.session.surface(true); window.quiet(); check(window.starts == 1, "surface restoration does not resume");
        window.session.play(); check(window.starts == 2, "explicit continue after surface restoration");
        window.session.active(false); window.session.play(); check(window.starts == 2, "background play ignored");
        System.out.println("PASS window interruption / surface teardown and restoration / background play denied");

        Rig paused = new Rig(); paused.session.active(true); paused.session.begin(); paused.session.pause(); paused.session.surface(true); paused.session.prepared(); paused.quiet();
        check(paused.starts == 0, "pause during prepare cancels autoplay"); paused.session.play(); check(paused.starts == 1, "explicit play remains possible");
        Rig inactive = new Rig(); inactive.session.begin(); inactive.session.surface(true); inactive.session.prepared(); inactive.session.active(true);
        check(inactive.requests == 0 && inactive.starts == 0, "inactive selection never requests focus or starts on return");
        System.out.println("PASS user pause during preparation / inactive selection / no focus request in background");

        Rig failure = new Rig(); failure.failStart = true; failure.ready(); failure.quiet(); failure.status(PlaybackSession.State.ERROR);
        failure.session.play(); failure.session.prepared(); check(failure.requests == 1, "decoder error cannot restart same invalid player");
        Rig requestFailure = new Rig(); requestFailure.failRequest = true; requestFailure.ready(); requestFailure.quiet(); requestFailure.status(PlaybackSession.State.FOCUS_DENIED);
        Rig abandonFailure = new Rig(); abandonFailure.ready(); abandonFailure.failAbandon = true; abandonFailure.session.pause(); abandonFailure.quiet();
        System.out.println("PASS decoder / focus request / abandon exceptions fail without playback intent");

        Rig error = new Rig(); error.ready(); error.session.error(); error.quiet(); error.session.active(false); error.session.surface(false); error.session.active(true); error.session.prepared();
        error.status(PlaybackSession.State.ERROR); check(error.starts == 1, "lifecycle cannot revive errored playback");
        System.out.println("PASS playback error releases focus / late lifecycle and prepared events cannot revive");
        System.out.println("TRACE prepare-loss " + String.join(" -> ", preparing.timeline));
        System.out.println("TRACE playing-loss " + String.join(" -> ", running.timeline));
        System.out.println("TRACE denied " + String.join(" -> ", denied.timeline));
    }
}

package android.os;

/** Only posts/removes callbacks; does not implement EPG, cache, time or networking. */
public final class Handler {
    private final Looper looper;
    public Handler(Looper looper) { this.looper = looper; }
    public boolean post(Runnable task) { return looper.queue.offer(new Looper.Posted(this, task)); }
    public void removeCallbacksAndMessages(Object ignored) {
        looper.queue.removeIf(posted -> posted.handler == this);
    }
}

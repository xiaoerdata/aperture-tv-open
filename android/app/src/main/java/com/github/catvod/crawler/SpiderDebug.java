package com.github.catvod.crawler;
/** Plugins must not send personal URLs or tokens to the application log. */
public final class SpiderDebug {
    private SpiderDebug() { }
    public static void log(String message) { }
    public static void log(Throwable error) { }
    public static void log(Exception error) { }
}

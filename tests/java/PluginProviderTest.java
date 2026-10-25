package org.zhixiaoce.aperture;

/** Old Android firmware may have a WebView far below the Android API version. */
public final class PluginProviderTest {
    private static int assertions;
    private static void rejected(String version,String expected)throws Exception {
        try{CinemaJsPlugin.requireProvider(version);throw new AssertionError("Accepted old/unknown WebView");}
        catch(java.io.IOException error){
            if(!error.getMessage().contains(expected)||!error.getMessage().contains("JAR 和 Python"))throw new AssertionError(error.getMessage());
            assertions++;
        }
    }
    public static void main(String[] args)throws Exception {
        rejected("58.0.3029.125","58.0.3029.125");
        rejected("60.0.3112.116","60.0.3112.116");
        rejected(null,"无法识别");
        rejected("unknown","无法识别");
        CinemaJsPlugin.requireProvider("61.0.3163.98");assertions++;
        CinemaJsPlugin.requireProvider("131.0.6778.260");assertions++;
        System.out.println(assertions+" WebView provider gate assertions passed");
    }
}

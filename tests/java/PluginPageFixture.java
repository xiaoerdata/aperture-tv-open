package org.zhixiaoce.aperture;
import java.nio.file.*;
public final class PluginPageFixture {
    public static void main(String[] args)throws Exception {
        System.out.print(CinemaJsPlugin.html(args[0],args.length>2?args[2]:"自有 ",new String(Files.readAllBytes(Paths.get(args[1])),java.nio.charset.StandardCharsets.UTF_8)));
    }
}

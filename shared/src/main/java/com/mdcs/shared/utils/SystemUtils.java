package com.mdcs.shared.utils;

import java.io.File;
import java.nio.file.Paths;

public class SystemUtils {

    public static String getOS(){
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) return "WINDOWS";

        else if (os.contains("mac")) return "MAC";
        
        else if (os.contains("nix") || os.contains("nux") || os.contains("aix"))
            return "LINUX";

        else return "OTHERS";
    }

    public static String getAppDataDirectory(){
        String appdata_path;

        switch (getOS()) {
            case "WINDOWS":
                appdata_path = Paths.get(System.getenv("APPDATA")).toString();
                break;

            case "LINUX":
                String home_path = System.getenv("XDG_CONFIG_HOME");
                String base = (home_path != null && !home_path.isEmpty())
                    ? home_path
                    : System.getProperty("user.home") + "./config";

                appdata_path = Paths.get(base).toString();

                break;

            case "MAC":
                appdata_path = Paths.get(
                    System.getProperty("user.home"), 
                    "Library", 
                    "Application Support"
                ).toString();

                break;

            default: appdata_path = Paths.get(System.getProperty("user.home")).toString();
        }

        return new File(appdata_path, "MDCS").toString();
    }
}

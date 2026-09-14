package com.mdcs.shared.logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mdcs.shared.fileio.FileIO;
import com.mdcs.shared.utils.SystemUtils;

public class Log{
    public Map<String, Queue<String>> logs;

    public static String formatTimestamp() {
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        return time.format(formatter) + "." + String.format("%09d", time.getNano());
    }

    private static String levelToFile(String level){
        switch (level.toLowerCase()) {
            case "error": return "Error";

            case "info": return "App";

            case "network": return "Network";
        
            default: return "App";
        }
    }

    // Set path based on the log type
    private static Path findPath(String doc_name){
        String path = Paths.get(SystemUtils.getAppDataDirectory(), "logs").toString();

        return Paths.get(path, doc_name + ".log");
    }

    public void error(String module, String message){
        String time = formatTimestamp();

        String line = String.format(
            "[ %s ] [ ERROR ] [ %s ] %s", 
            time, module, message
        );

        this.logs.get(levelToFile("error")).add(line);
    }

    public void info(String module, String message){
        String time = formatTimestamp();

        String line = String.format(
            "[ %s ] [ INFO ] [ %s ] %s", 
            time, module, message
        );

        this.logs.get(levelToFile("info")).add(line);
    }

    public void warn(String module, String message){
        String time = formatTimestamp();

        String line = String.format(
            "[ %s ] [ WARN ] [ %s ] %s", 
            time, module, message
        );

        this.logs.get(levelToFile("warn")).add(line);
    }

    public void network(String module, String message){
        String time = formatTimestamp();

        String line = String.format(
            "[ %s ] [ NETWORK ] [ %s ] %s", 
            time, module, message
        );

        this.logs.get(levelToFile("network")).add(line);
    }

    // Write logs into respected file
    public void flush() throws IOException{
        
        for (String divison : this.logs.keySet()){
            String content = "";
            Path path = findPath(divison);

            for (String entry : this.logs.get(divison)) content += (entry + "\n");

            if (!content.isEmpty())
                FileIO.fileWrite(path.toString(), content, "append");

            this.logs.get(divison).clear();
        }
    }

    public Log(){
        this.logs = new ConcurrentHashMap<>();
        this.logs.put("App", new ConcurrentLinkedQueue<>());
        this.logs.put("Network", new ConcurrentLinkedQueue<>());
        this.logs.put("Error", new ConcurrentLinkedQueue<>());
    }
}
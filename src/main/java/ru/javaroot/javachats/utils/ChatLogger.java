package ru.javaroot.javachats.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatLogger {
    private File logFile;
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void init(File logsFolder) {
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        String fileName = fileDateFormat.format(new Date()) + ".log";
        this.logFile = new File(logsFolder, fileName);
        try {
            if (this.logFile.createNewFile()) {
                log("Logger initialized. Server started.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void log(String message) {
        if (logFile == null)
            return;
        String timestamp = logDateFormat.format(new Date());
        try (FileWriter fw = new FileWriter(logFile, true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

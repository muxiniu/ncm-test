package com.muxiniu.ncmtest;

import android.content.Context;
import android.os.Process;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public static void init(Context ctx) { Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(ctx)); }

    private CrashHandler(Context ctx) { this.context = ctx; this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler(); }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "crash_logs");
            logDir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File logFile = new File(logDir, "crash_" + ts + ".txt");
            PrintWriter pw = new PrintWriter(new FileWriter(logFile));
            pw.println("NCM Decoder Pro Crash Report"); pw.println("Time: " + ts); pw.println("Thread: " + thread.getName()); pw.println();
            ex.printStackTrace(pw); pw.close();
        } catch (Exception e) {}
        if (defaultHandler != null) defaultHandler.uncaughtException(thread, ex);
        else Process.killProcess(Process.myPid());
    }
}

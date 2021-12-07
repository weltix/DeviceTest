package ua.com.ekka.devicetest.log;

import android.os.Environment;

import de.mindpipe.android.logging.log4j.LogConfigurator;

public class Log4jHelper {
    private final static LogConfigurator mLogConfigrator = new LogConfigurator();

    static {
        configureLog4j();
    }

    private static void configureLog4j() {
        String fileName = Environment.getExternalStorageDirectory() + "/" + "DeviceTest.log";
        String filePattern = "%d - [%c] - %p : %m%n";
        int maxBackupSize = 5;
        long maxFileSize = 1024 * 1024 * 10;
        configure(fileName, filePattern, maxBackupSize, maxFileSize);
    }

    private static void configure(String fileName, String filePattern, int maxBackupSize, long maxFileSize) {
        mLogConfigrator.setFileName(fileName);
        mLogConfigrator.setMaxFileSize(maxFileSize);
        mLogConfigrator.setFilePattern(filePattern);
        mLogConfigrator.setMaxBackupSize(maxBackupSize);
        mLogConfigrator.setUseLogCatAppender(true);
        mLogConfigrator.configure();

    }

    public static org.apache.log4j.Logger getLogger(String name) {
        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(name);
        return logger;
    }
}

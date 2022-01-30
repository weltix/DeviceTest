package ua.com.ekka.devicetest.log;

import android.os.Build;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.rolling.RollingFileAppender;
import ua.com.ekka.devicetest.BuildConfig;

/**
 * Class provides adding to start of every log file some important information about app and OS.
 */
public class MyRollingFileAppender extends RollingFileAppender {

    private static Logger logger = LoggerFactory.getLogger(MyRollingFileAppender.class);

    private static String androidID;
    private static long logSystemInfoLastTime = 0;

    @Override
    public void rollover() {
        super.rollover();
        new Thread(() -> {
            logSystemInfo(null);
        }).start();
    }

    /**
     * Logs important information about app and OS.
     * Implemented time pause between sequential executing of method
     * (workaround for duplicated logging when start of app + rollingFileAppender).
     */
    public static synchronized void logSystemInfo(@Nullable String id) {
        if (System.currentTimeMillis() < (logSystemInfoLastTime + 3000))
            return;
        logSystemInfoLastTime = System.currentTimeMillis();

        String eventName;
        if (id != null) {
            eventName = "START";
            androidID = id;
        } else {
            eventName = "ROLLBACK";
        }
        logger.info(String.format("*** %s ***, %s, build: %d / %s",
                eventName, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_CODE, Build.PRODUCT));
        logger.info("androidID: " + androidID);
        logger.info("Model: " + android.os.Build.MODEL);
        logger.info("Brand: " + android.os.Build.BRAND);
        logger.info("Product: " + android.os.Build.PRODUCT);
        logger.info("Device: " + android.os.Build.DEVICE);
        logger.info("Codename: " + android.os.Build.VERSION.CODENAME);
        logger.info("Release: " + android.os.Build.VERSION.RELEASE);
    }
}
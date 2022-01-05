package ua.com.ekka.devicetest;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

public class MainActivity extends AppCompatActivity {

    public static Logger logger = null;
    private static final String TAG = MainActivity.class.getSimpleName();

    // values of next constants are returned by android.os.Build.PRODUCT
    public static final String PRODUCT_AOSP_DRONE2 = "aosp_drone2";
    public static final String PRODUCT_RES_PX30 = "res_px30";
    public static final String PRODUCT_RES_RK3399 = "res_rk3399";

    private Button buttonTestComPort;
    private Button buttonTestTouchScreen;

    private Timer clockTimer;

    // Register the permissions callback, which handles the user's response to the system permissions dialog.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "Permission dialog: necessary permission is granted by user in system dialog.");
                } else {
                    this.requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    Log.i(TAG, "Permission dialog: permission denied by user in system dialog, so launch dialog again, because further app work is meaningless.");
                }
            });

    private View.OnClickListener buttonClickListener = v -> {
        Button button = (Button) v;
        switch (button.getId()) {
            case R.id.button_test_com_port:
                logger.info("onClick() button_test_com_port");
                Intent intent = new Intent(this, TestComPortActivity.class);
                startActivity(intent);
                break;
            case R.id.button_test_touch_screen:
                logger.info("onClick() button_test_touch_screen");
                break;
            default:
                break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get screen dimensions
        Display display = this.getWindowManager().getDefaultDisplay();
        Point sizeScreen = new Point();
        display.getSize(sizeScreen);
        sizeScreen.y += 48;
        if (Build.PRODUCT.equals(PRODUCT_RES_RK3399))
            sizeScreen.y += 8;

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                Log.d(TAG, "onCreate(), screen size before input tap on permission dialog - x:" + sizeScreen.x + ", y:" + sizeScreen.y);
                if (sizeScreen.x == 1920)  // 14' "res_rk3399"
                    SuCommandsHelper.executeCmd("input tap 1165 560", 0);  // don't use timeout here; tap is executed near 1300ms, and this time is enough for system dialog to be opened completely, and confirm button become visible
                else                       // 10' "res_px30"
                    SuCommandsHelper.executeCmd("input tap 845 445", 0);   // don't use timeout here; tap is executed near 1300ms, and this time is enough for system dialog to be opened completely, and confirm button become visible
                System.exit(0);     // workaround - app restarts again (don't know why, but it works)
            } else {
                Log.i(TAG, "onCreate(), permission was already granted");
            }
        }

        try {
            Log.i(TAG, "onCreate(), create (org.apache.log4j.Logger) logger...");
            logger = Log4jHelper.getLogger(TAG);  // here will be uncaught RuntimeException if WRITE_EXTERNAL_STORAGE permission not granted
        } catch (Exception e) {
            Log.e(TAG, "Log4jHelper.getLogger()", e);
        }
        logger.info("onCreate(), screen size x:" + sizeScreen.x + ", y:" + sizeScreen.y);

        TextView textViewAppVersionName = findViewById(R.id.textview_app_version_name);
        textViewAppVersionName.setText("v" + BuildConfig.VERSION_NAME);

        buttonTestComPort = findViewById(R.id.button_test_com_port);
        buttonTestTouchScreen = findViewById(R.id.button_test_touch_screen);

        buttonTestComPort.setOnClickListener(buttonClickListener);
        buttonTestTouchScreen.setOnClickListener(buttonClickListener);

        DateFormat dateFormat0 = new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("uk"));
        DateFormat dateFormat1 = new SimpleDateFormat("dd.MM.yyyy HH mm", new Locale("uk"));
        startClock(dateFormat0, dateFormat1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockTimer != null) {
            clockTimer.cancel();
            clockTimer = null;
        }
        logger.info("onDestroy()");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName("com.resonance.cashdisplay", "com.resonance.cashdisplay.MainActivity"));
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        boolean isIntentSafe = activities.size() > 0;
        if (isIntentSafe) {
            startActivity(intent);
            System.exit(0);
        } else {
            logger.warn("Try but fails to launch package com.resonance.cashdisplay, because it is absent in system");
        }
    }

    /**
     * Provides clock.
     * Every 500ms specified TextView will set text, provided in arguments to this method:
     * arg1 - arg2 - arg1 - arg2 ....
     * If second argument == null, then only 1-st argument will change specified TextView:
     * arg1 - arg1 - arg1 - arg1 ....
     *
     * @param dateFormat0 main format, must be present necessarily;
     * @param dateFormat1 is optional, and may be equal {@code null}.
     */
    protected void startClock(DateFormat dateFormat0, @Nullable DateFormat dateFormat1) {
        if (clockTimer == null) {
            clockTimer = new Timer();
            TextView textViewDateTime = findViewById(R.id.textview_date_time);
            Date date = new Date(System.currentTimeMillis());
            clockTimer.scheduleAtFixedRate(new TimerTask() {
                boolean flip = true;

                @Override
                public void run() {
                    date.setTime(System.currentTimeMillis());
                    if (flip) {
                        textViewDateTime.post(() ->
                                textViewDateTime.setText(dateFormat0.format(date)));
                        if (dateFormat1 == null)
                            return;
                    } else {
                        textViewDateTime.post(() ->
                                textViewDateTime.setText(dateFormat1.format(date)));
                    }
                    flip = !flip;
                }
            }, 0, 500);
        }
    }
}
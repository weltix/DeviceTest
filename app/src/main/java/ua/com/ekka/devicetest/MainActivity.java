package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.uart.UartWorker.SERIAL_PORTS;
import static ua.com.ekka.devicetest.uart.UartWorker.baudrates;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;
import ua.com.ekka.devicetest.uart.UartWorker;

public class MainActivity extends AppCompatActivity {

    Logger logger = null;
    private static final String TAG = MainActivity.class.getSimpleName();

    // values of next constants are returned by android.os.Build.PRODUCT
    public static final String PRODUCT_AOSP_DRONE2 = "aosp_drone2";
    public static final String PRODUCT_RES_PX30 = "res_px30";
    public static final String PRODUCT_RES_RK3399 = "res_rk3399";

    private UartWorker uartWorker;

    private Handler uartEventsHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UartWorker.EVENT_UART_READ:
                    logger.debug("EVENT_UART_READ");
//                    cmdParser.parseInputStr((byte[]) msg.obj, msg.arg2);
                    break;
                case UartWorker.EVENT_UART_OPEN:
                    String[] obj0 = (String[]) msg.obj;
                    logger.debug(String.format("EVENT_UART_OPENED, %s (%s, 8, N, 1)", obj0[0], obj0[1]));
                    break;
                case UartWorker.EVENT_UART_CLOSED:
                    String[] obj1 = (String[]) msg.obj;
                    logger.debug(String.format("EVENT_UART_CLOSED, %s (%s, 8, N, 1)", obj1[0], obj1[1]));
//                    setVisibleContext(CONTEXT_CONNECT, null);
                    break;
                case UartWorker.EVENT_UART_ERROR:
                    String[] obj2 = (String[]) msg.obj;
                    if (msg.arg2 == 0)
                        logger.debug(String.format("EVENT_UART_ERROR, %s (%s, 8, N, 1), when %s", obj2[0], obj2[1], obj2[2]));
                    break;
            }
        }
    };

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
                try {
                    Thread.sleep(1000);  // here Android needs at least 100ms to save granted permission
                } catch (InterruptedException e) {
                    Log.e(TAG, "onCreate(), " + e.toString());
                }
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

        uartWorker = new UartWorker(uartEventsHandler, this);
        uartWorker.openPort(SERIAL_PORTS[0], baudrates[0]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.info("onDestroy()");
        if (uartWorker != null)
            uartWorker.unregisterUartChangeSettingsReceiver();
    }
}
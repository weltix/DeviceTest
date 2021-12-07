package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.uart.UartWorker.SERIAL_PORTS_AOSP_DRONE2;
import static ua.com.ekka.devicetest.uart.UartWorker.baudrates;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

    // values of next constants are returned by android.os.Build.PRODUCT
    public static final String PRODUCT_AOSP_DRONE2 = "aosp_drone2";
    public static final String PRODUCT_RES_PX30 = "res_px30";
    public static final String PRODUCT_RES_RK3399 = "res_rk3399";

    private Point sizeScreen;

    private UartWorker uartWorker;

    private Handler uartEventsHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UartWorker.EVENT_UART_READ:
//                    cmdParser.parseInputStr(msg.arg2, (byte[]) msg.obj);
                    logger.debug("EVENT_UART_READ");
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
                    logger.debug(String.format("EVENT_UART_ERROR, %s (%s, 8, N, 1), when %s", obj2[0], obj2[1], obj2[2]));
                    break;
            }
        }
    };

    // Register the permissions callback, which handles the user's response to the system permissions dialog.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
//                    imageWarning.setVisibility(View.INVISIBLE);
//                    viewModel.setStatusConnection("");
//                    openSerialPort();
                    logger.info("Necessary permissions are granted by user in system dialog");
                } else {
                    this.requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    logger.info("Permissions are denied by user in system dialog, so launch dialog again, because further app work is meaningless");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            logger = Log4jHelper.getLogger(MainActivity.class.getName());
        } catch (Exception e) {
//            Log.e();
        }

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
//                onFailureHappened(R.drawable.warning, "ДОДАТКУ НЕ НАДАНО НЕОБХIДНИХ ДОЗВОЛIВ", "Necessary permissions denied by user", 80);
                logger.debug("Size screen before input tap on permission dialog - x:" + sizeScreen.x + ", y:" + sizeScreen.y);
                runOnUiThread(() -> {  // execute after dialog appears for surely (not because we want execute tap in main thread)
                    if (sizeScreen.x == 1920) //14"
                        SuCommandsHelper.executeCmd("input tap  1166 592", 0);  // don't use timeout here
                    else                      //10"
                        SuCommandsHelper.executeCmd("input tap 845 445", 0);  // don't use timeout here
                    System.exit(0);  // workaround - app restarts again (don't know why, but it works)
                });
            }
        }

        logger.info("onCreate()");

        // Get screen dimensions
        Display display = this.getWindowManager().getDefaultDisplay();
        sizeScreen = new Point();
        display.getSize(sizeScreen);
        sizeScreen.y += 48;
        if (Build.PRODUCT.equals(PRODUCT_RES_RK3399))
            sizeScreen.y += 8;
        logger.debug("Size screen x:" + sizeScreen.x + ", y:" + sizeScreen.y);

        uartWorker = new UartWorker(uartEventsHandler, this);
        uartWorker.openPort(SERIAL_PORTS_AOSP_DRONE2[1], baudrates[0]);


    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");

    }
}
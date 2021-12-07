package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.uart.UartWorker.SERIAL_PORTS_AOSP_DRONE2;
import static ua.com.ekka.devicetest.uart.UartWorker.baudrates;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.uart.UartWorker;

public class MainActivity extends AppCompatActivity {

    Logger logger = Log4jHelper.getLogger(MainActivity.class.getName());

    // values of next constants are returned by android.os.Build.PRODUCT
    public static final String PRODUCT_AOSP_DRONE2 = "aosp_drone2";
    public static final String PRODUCT_RES_PX30 = "res_px30";
    public static final String PRODUCT_RES_RK3399 = "res_rk3399";

    private UartWorker uartWorker;

    private Handler uartEventsHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String[] msgObj = (String[]) msg.obj;
            switch (msg.what) {
                case UartWorker.EVENT_UART_READ:
                    logger.warn("EVENT_UART_READ");
//                    cmdParser.parseInputStr((byte[]) msg.obj, msg.arg2);
                    break;
                case UartWorker.EVENT_UART_OPEN:
                    logger.debug(String.format("EVENT_UART_OPENED, %s (%s, 8, N, 1)", msgObj[0], msgObj[1]));
                    break;
                case UartWorker.EVENT_UART_CLOSED:
//                    logger.error(String.format("EVENT_UART_CLOSED, %s (%s, 8, N, 1)", msgObj[0], msgObj[1]));
                    //                    setVisibleContext(CONTEXT_CONNECT, null);
                    break;
                case UartWorker.EVENT_UART_ERROR:
                    logger.debug(String.format("EVENT_UART_ERROR, %s (%s, 8, N, 1), when %s", msgObj[0], msgObj[1], msgObj[2]));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        logger.info("onCreate()");

        uartWorker = new UartWorker(uartEventsHandler, this);
        uartWorker.openPort(SERIAL_PORTS_AOSP_DRONE2[1], baudrates[0]);

//        MediaPlayer music = MediaPlayer.create(MainActivity.this, R.raw.phone_incoming_call);
//        music.start();
    }
}
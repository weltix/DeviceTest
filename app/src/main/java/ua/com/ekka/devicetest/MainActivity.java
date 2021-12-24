package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.uart.UartWorker.baudrates;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

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

    private TextView textViewTestStatus;
    private TextView textViewNowTestedCom;
    private TextView textViewNowTestedBaudrate;
    private TextView textViewTestResult;
    private Button buttonStart;
    private Button buttonStop;

    private int selectedRadioButtonComId = R.id.radio_button_com_1;

    private UartWorker uartWorker;

    private Thread testingThread;
    private String sendingTestString;
    private BlockingQueue<String> blockingQueueForReceivedTestString = new ArrayBlockingQueue<>(1);
    private StringBuilder receivedTestStringBuilder = new StringBuilder();

    private static HandlerThread uartEventsHandlerThread = new HandlerThread("uartEventsHandlerThread");

    static {
        uartEventsHandlerThread.start();
    }

    private Handler uartEventsHandler = new Handler(uartEventsHandlerThread.getLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UartWorker.EVENT_UART_READ:
                    int strLength = msg.arg2;
                    String str = new String((byte[]) msg.obj, 0, strLength, StandardCharsets.UTF_8);
                    receivedTestStringBuilder.append(str);
                    if (receivedTestStringBuilder.length() == sendingTestString.length())
                        blockingQueueForReceivedTestString.add(receivedTestStringBuilder.toString());
//                    logger.debug("EVENT_UART_READ, str=" + str);
                    break;
                case UartWorker.EVENT_UART_OPEN:
                    int comNum_ = msg.arg2;
                    String[] obj0 = (String[]) msg.obj;
                    runOnUiThread(() -> {
                        buttonStop.setEnabled(true);
                        textViewTestStatus.setText(getString(R.string.testing_now));
                        textViewNowTestedCom.setText("COM" + comNum_);
                        textViewNowTestedBaudrate.setText(obj0[1]);
                        textViewTestStatus.setVisibility(View.VISIBLE);
                        textViewNowTestedCom.setVisibility(View.VISIBLE);
                        textViewNowTestedBaudrate.setVisibility(View.VISIBLE);
                    });
//                    logger.debug(String.format("EVENT_UART_OPENED, %s (%s, 8, N, 1) (COM%d)", obj0[0], obj0[1], msg.arg2));
                    break;
                case UartWorker.EVENT_UART_CLOSED:
                    String[] obj1 = (String[]) msg.obj;
//                    logger.debug(String.format("EVENT_UART_CLOSED, %s (%s, 8, N, 1) (COM%d)", obj1[0], obj1[1], msg.arg2));
                    break;
                case UartWorker.EVENT_UART_ERROR:
                    String[] obj2 = (String[]) msg.obj;
                    int errorCode = msg.arg1;
                    int comNum__ = msg.arg2;
                    if (errorCode == 0) {
                        testingThread.interrupt();  // better here, not for all UartWorker.EVENT_UART_ERROR, for case if in future will be added error event, that no need this method to call
                        runOnUiThread(() -> {
                            displayFailTestResult(String.format(getString(R.string.error_opening_com_port), comNum__));
                            new Handler().postDelayed(() -> {
                                textViewTestResult.setVisibility(View.INVISIBLE);
                                buttonStop.performClick();
                            }, 1700);
                        });
                    } else if (errorCode == 1) {
                        testingThread.interrupt();  // better here, not for all UartWorker.EVENT_UART_ERROR, for case if in future will be added error event, that no need this method to call
                        runOnUiThread(() -> {
                            displayFailTestResult(String.format(getString(R.string.error_writing_to_com_port), comNum__));
                            new Handler().post(() -> buttonStop.performClick());
                        });
                    }
                    logger.debug(String.format("EVENT_UART_ERROR, %s (%s, 8, N, 1), when %s", obj2[0], obj2[1], obj2[2]));
                    break;
            }
        }
    };

    private View.OnClickListener radioButtonClickListener = v -> {
        RadioButton radioButton = (RadioButton) v;
        if (selectedRadioButtonComId != radioButton.getId()) {
            selectedRadioButtonComId = radioButton.getId();
            buttonStop.performClick();
        }
        switch (radioButton.getId()) {
            case R.id.radio_button_com_1:
                break;
            case R.id.radio_button_com_2:
                break;
            default:
                break;
        }
    };

    private View.OnClickListener buttonClickListener = v -> {
        Button button = (Button) v;
        switch (button.getId()) {
            case R.id.button_start:
                buttonStart.setEnabled(false);
                textViewTestResult.setVisibility(View.INVISIBLE);
                textViewTestResult.setText("");
                textViewTestResult.setTextColor(getResources().getColor(R.color.green_dark));
                String selectedCom = String.valueOf(((RadioButton) findViewById(selectedRadioButtonComId)).getText());
                String selectedComNumber = selectedCom.substring(selectedCom.length() - 1);
                Integer selectedComNum = Integer.parseInt(selectedComNumber);
                runRxdTxdTest(selectedComNum);
                break;
            case R.id.button_stop:
                if (testingThread != null)
                    testingThread.interrupt();
                uartWorker.closePort();
                buttonStop.setEnabled(false);
                buttonStart.setEnabled(true);
                textViewTestStatus.setVisibility(View.INVISIBLE);
                textViewNowTestedCom.setVisibility(View.INVISIBLE);
                textViewNowTestedBaudrate.setVisibility(View.INVISIBLE);
                textViewTestStatus.setText("");
                textViewNowTestedCom.setText("");
                textViewNowTestedBaudrate.setText("");
                break;
            default:
                break;
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

        RadioButton radioButtonCom1 = findViewById(R.id.radio_button_com_1);
        radioButtonCom1.setOnClickListener(radioButtonClickListener);
        RadioButton radioButtonCom2 = findViewById(R.id.radio_button_com_2);
        radioButtonCom2.setOnClickListener(radioButtonClickListener);

        textViewTestStatus = findViewById(R.id.textview_test_status);
        textViewNowTestedCom = findViewById(R.id.textview_now_testing_com);
        textViewNowTestedBaudrate = findViewById(R.id.textview_now_testing_baudrate);
        textViewTestResult = findViewById(R.id.textview_test_result);
        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);

        buttonStart.setOnClickListener(buttonClickListener);
        buttonStop.setOnClickListener(buttonClickListener);

        uartWorker = new UartWorker(uartEventsHandler);
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
    }

    private void displayFailTestResult(String msg) {
        textViewTestResult.setText(msg);
        textViewTestResult.setTextColor(getResources().getColor(R.color.red_orange));
        textViewTestResult.setVisibility(View.VISIBLE);
    }

    private void runRxdTxdTest(int selectedComNum) {
        testingThread = new Thread(() -> {
            String resultTestString = "";  // all or nothing - either this string remains empty (or become null), or it will receive full complete testing string
            for (int currentBaudrate : baudrates) {
                receivedTestStringBuilder.setLength(0);
                receivedTestStringBuilder.trimToSize();
                blockingQueueForReceivedTestString.clear();

                String textFile = "TextForTest.txt";
                byte[] buffer = null;
                InputStream inputStream;
                try {
                    inputStream = getAssets().open(textFile);
                    int size = inputStream.available();
                    buffer = new byte[size];
                    inputStream.read(buffer);
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sendingTestString = new String(buffer);

                uartWorker.openPort(selectedComNum, currentBaudrate);
                uartWorker.sendData(sendingTestString);
                try {
                    resultTestString = blockingQueueForReceivedTestString.poll(7000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                boolean isDtrDsrTestPassed = (uartWorker.getDTR() == uartWorker.getDSR());
                uartWorker.closePort();

                if (Thread.currentThread().isInterrupted()) {  // case when UartWorker.EVENT_UART_ERROR message received by uartEventsHandler
                    return;
                }

                int realReceivedLength = receivedTestStringBuilder.length();
                if (realReceivedLength == 0 && !isDtrDsrTestPassed) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.test_plug_not_connected), selectedComNum));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Plug inputStream not connected to COM%d obviously", selectedComNum));
                    return;
                } else if (!sendingTestString.equals(resultTestString)) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.com_n_dont_pass_test_rxd_txd), selectedComNum));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("COM%d fails test RxD-TxD", selectedComNum));
                    return;
                } else if (!isDtrDsrTestPassed) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.com_n_dont_pass_test_dtr_dsr), selectedComNum));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("COM%d fails test DTR-DSR", selectedComNum));
                    return;
                }
//                if (sendingTestString.equals(resultTestString))
//                    logger.warn(String.format("OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOK COM%d %d", selectedComNum, currentBaudrate));
            }

            if (Thread.currentThread().isInterrupted()) {  // case when UartWorker.EVENT_UART_ERROR message received by uartEventsHandler or
                return;
            }

            runOnUiThread(() -> {
                textViewTestResult.setText(String.format(getString(R.string.com_n_pass_test_successfully), selectedComNum));
                textViewTestResult.setVisibility(View.VISIBLE);
                buttonStop.performClick();
                logger.warn(String.format("COM%d tested successfully", selectedComNum));
            });
        });
        testingThread.start();
    }
}
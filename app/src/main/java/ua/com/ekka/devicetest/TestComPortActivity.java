package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_PX30;
import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_RK3399;
import static ua.com.ekka.devicetest.uart.UartWorker.baudrates;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.uart.UartWorker;

public class TestComPortActivity extends AppCompatActivity {

    private static final String TAG = TestComPortActivity.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    private CheckBox checkBoxBaudratesDirection;
    private TextView textViewTestStatus;
    private ProgressBar progressBar;
    private TextView textViewNowTestedCom;
    private TextView textViewNowTestedBaudrate;
    private TextView textViewTestResult;
    private TextView textViewTestComPortOnlyRxdTxd;
    private Button buttonStart;
    private Button buttonStop;

    private int selectedRadioButtonComId;

    private UartWorker uartWorker;

    private Thread testingThread;
    private String sendingTestString;
    private BlockingQueue<String> blockingQueueForReceivedTestString;
    private StringBuilder receivedTestStringBuilder;

    private Handler uartEventsHandler;
    private HandlerThread uartEventsHandlerThread;

    private class UartEventsHandler extends Handler {
        public UartEventsHandler(Looper looper) {
            super(looper);
        }

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
                        textViewNowTestedCom.setText("         порт: COM" + comNum_);
                        textViewNowTestedBaudrate.setText("скорость: " + obj0[1]);
                        checkBoxBaudratesDirection.setEnabled(false);
                        textViewTestStatus.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
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
                            displayFailTestResult(String.format(getString(R.string.error_opening_com_port), comNum__, obj2[1]));
                            new Handler().postDelayed(() -> {
                                textViewTestResult.setVisibility(View.INVISIBLE);
                                buttonStop.performClick();
                            }, 1700);
                        });
                    } else if (errorCode == 1) {
                        testingThread.interrupt();  // better here, not for all UartWorker.EVENT_UART_ERROR, for case if in future will be added error event, that no need this method to call
                        runOnUiThread(() -> {
                            displayFailTestResult(String.format(getString(R.string.error_writing_to_com_port), comNum__, obj2[1]));
                            new Handler().post(() -> buttonStop.performClick());
                        });
                    }
                    logger.debug(String.format("EVENT_UART_ERROR, %s (%s, 8, N, 1), when %s", obj2[0], obj2[1], obj2[2]));
                    break;
            }
        }
    }

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
                logger.info("onClick() button_start");
                buttonStart.setEnabled(false);
                textViewTestResult.setVisibility(View.INVISIBLE);
                textViewTestResult.setText("");
                textViewTestResult.setTextColor(getResources().getColor(R.color.green_dark));
                String selectedCom = String.valueOf(((RadioButton) findViewById(selectedRadioButtonComId)).getText());
                String selectedComNumber = selectedCom.substring(selectedCom.length() - 1);
                Integer selectedComNum = Integer.parseInt(selectedComNumber);
                runTest(selectedComNum);
                break;
            case R.id.button_stop:
                logger.info("onClick() button_stop");
                if (testingThread != null)
                    testingThread.interrupt();
                uartWorker.closePort();
                buttonStop.setEnabled(false);
                checkBoxBaudratesDirection.setEnabled(true);
                textViewTestStatus.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                textViewNowTestedCom.setVisibility(View.INVISIBLE);
                textViewNowTestedBaudrate.setVisibility(View.INVISIBLE);
                textViewTestStatus.setText("");
                textViewNowTestedCom.setText("");
                textViewNowTestedBaudrate.setText("");
                new Thread(() -> {
                    try {
                        while (testingThread.isAlive()) {
                            Thread.currentThread().sleep(70);
                        }
                    } catch (InterruptedException | NullPointerException e) {
                    }
                    runOnUiThread(() -> buttonStart.setEnabled(true));
                }).start();
                break;
            default:
                break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.info("onCreate()");
        setContentView(R.layout.activity_com_port);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        RadioButton radioButtonCom1 = findViewById(R.id.radio_button_com_1);
        radioButtonCom1.setOnClickListener(radioButtonClickListener);
        RadioButton radioButtonCom2 = findViewById(R.id.radio_button_com_2);
        radioButtonCom2.setOnClickListener(radioButtonClickListener);

        selectedRadioButtonComId = R.id.radio_button_com_1;

        checkBoxBaudratesDirection = findViewById(R.id.checkbox_baudrates_direction);
        textViewTestStatus = findViewById(R.id.textview_test_status);
        progressBar = findViewById(R.id.progress_bar);
        textViewNowTestedCom = findViewById(R.id.textview_now_testing_com);
        textViewNowTestedBaudrate = findViewById(R.id.textview_now_testing_baudrate);
        textViewTestResult = findViewById(R.id.textview_test_result);
        textViewTestComPortOnlyRxdTxd = findViewById(R.id.textview_test_com_port_only_rxd_txd);
        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);

        buttonStart.setOnClickListener(buttonClickListener);
        buttonStop.setOnClickListener(buttonClickListener);

        blockingQueueForReceivedTestString = new ArrayBlockingQueue<>(1);
        receivedTestStringBuilder = new StringBuilder();

        if (!Build.PRODUCT.equals(PRODUCT_RES_PX30) && !Build.PRODUCT.equals(PRODUCT_RES_RK3399))
            textViewTestComPortOnlyRxdTxd.setVisibility(View.VISIBLE);

        uartEventsHandlerThread = new HandlerThread("uartEventsHandlerThread");
        uartEventsHandlerThread.start();
        uartEventsHandler = new UartEventsHandler(uartEventsHandlerThread.getLooper());
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
        buttonStop.performClick();  // close port and stop "testingThread"
        uartEventsHandlerThread.quitSafely();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void displayFailTestResult(String msg) {
        textViewTestResult.setText(msg);
        textViewTestResult.setTextColor(getResources().getColor(R.color.red_orange));
        textViewTestResult.setVisibility(View.VISIBLE);
    }

    private void runTest(int selectedComNum) {
        testingThread = new Thread(() -> {
            String textFile = "TextForTest.txt";  // from assets directory
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

            Integer[] baudratesCopy = Arrays.copyOf(baudrates, baudrates.length);
            if (checkBoxBaudratesDirection.isChecked())
                Arrays.sort(baudratesCopy, Collections.reverseOrder());

            for (int currentBaudrate : baudratesCopy) {
                String receivedTestStringFinal = "";  // all or nothing - either this string remains empty (or become null), or it will receive full complete testing string
                receivedTestStringBuilder.setLength(0);
                receivedTestStringBuilder.trimToSize();
                blockingQueueForReceivedTestString.clear();

                uartWorker.openPort(selectedComNum, currentBaudrate);
                uartWorker.sendData(sendingTestString);
                try {
                    receivedTestStringFinal = blockingQueueForReceivedTestString.poll(7000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                boolean isDtrDsrTestPassed = (uartWorker.getDTR() == uartWorker.getDSR());
                uartWorker.closePort();
                isDtrDsrTestPassed = (uartWorker.getDTR() == uartWorker.getDSR()) && isDtrDsrTestPassed;

                if (Thread.currentThread().isInterrupted()) {  // case when UartWorker.EVENT_UART_ERROR message received by uartEventsHandler
                    return;
                }
                int realReceivedLength = receivedTestStringBuilder.length();
                logger.debug("realReceivedLength=" + realReceivedLength + " (expect " + sendingTestString.length() + ")");
                logger.debug("sendingTestString.equals(receivedTestStringFinal)=" + sendingTestString.equals(receivedTestStringFinal));
                logger.debug("isDtrDsrTestPassed=" + isDtrDsrTestPassed);
                if (realReceivedLength == 0 && !isDtrDsrTestPassed) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.test_plug_not_connected), selectedComNum, currentBaudrate));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Test plug not connected (COM%d, %d)", selectedComNum, currentBaudrate));
                    return;
                } else if (!sendingTestString.equals(receivedTestStringFinal) && !isDtrDsrTestPassed) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.error_testing_rxd_txd_dtr_dsr), selectedComNum, currentBaudrate));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Error testing both: RXD-TXD and DTR-DSR lines (COM%d, %d)", selectedComNum, currentBaudrate));
                    return;
                } else if (!sendingTestString.equals(receivedTestStringFinal)) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.error_testing_rxd_txd), selectedComNum, currentBaudrate));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Error testing RXD-TXD lines (COM%d, %d)", selectedComNum, currentBaudrate));
                    return;
                } else if (!isDtrDsrTestPassed) {
                    runOnUiThread(() -> {
                        displayFailTestResult(String.format(getString(R.string.error_testing_dtr_dsr), selectedComNum, currentBaudrate));
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Error testing DTR-DSR lines (COM%d, %d)", selectedComNum, currentBaudrate));
                    return;
                }
//                if (sendingTestString.equals(receivedTestStringFinal))
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
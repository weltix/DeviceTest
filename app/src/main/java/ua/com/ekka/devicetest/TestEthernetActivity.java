package ua.com.ekka.devicetest;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.eth.EthernetHelper;
import ua.com.ekka.devicetest.eth.IP_Settings;
import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

public class TestEthernetActivity extends AppCompatActivity {

    private static final String TAG = TestEthernetActivity.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    private Button buttonStart;
    private Button buttonStop;

    private TextView textViewTestResult;

    private Thread testingThread;

    private View.OnClickListener buttonClickListener = v -> {
        Button button = (Button) v;
        switch (button.getId()) {
            case R.id.button_start:
                logger.info("onClick() button_start");
                buttonStart.setEnabled(false);
                buttonStop.setEnabled(true);
                textViewTestResult.setVisibility(View.INVISIBLE);
                textViewTestResult.setText("");
                textViewTestResult.setTextColor(getResources().getColor(R.color.green_dark));
                runTest();
                break;
            case R.id.button_stop:
                logger.info("onClick() button_stop");
                if (testingThread != null)
                    testingThread.interrupt();
                buttonStop.setEnabled(false);
//                textViewTestStatus.setVisibility(View.INVISIBLE);
//                progressBar.setVisibility(View.INVISIBLE);
//                textViewNowTestedCom.setVisibility(View.INVISIBLE);
//                textViewNowTestedBaudrate.setVisibility(View.INVISIBLE);
//                textViewTestStatus.setText("");
//                textViewNowTestedCom.setText("");
//                textViewNowTestedBaudrate.setText("");
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
        setContentView(R.layout.activity_ethernet);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        textViewTestResult = findViewById(R.id.textview_test_result);

        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);

        buttonStart.setOnClickListener(buttonClickListener);
        buttonStop.setOnClickListener(buttonClickListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");
        EthernetHelper ethernetHelper = EthernetHelper.getInstance(this);
        IP_Settings ipSettings = ethernetHelper.ipSettings;

        TextView textViewIp = findViewById(R.id.textview_ip);
        TextView textViewMask = findViewById(R.id.textview_mask);
        TextView textViewGateway = findViewById(R.id.textview_gateway);
        textViewIp.setText(String.format("         IP: %s", ipSettings.getIp()));
        textViewMask.setText(String.format("маска: %s", ipSettings.getNetmask()));
        textViewGateway.setText(String.format(" шлюз: %s", ipSettings.getGateway()));

        logger.debug(ethernetHelper.ipSettings.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.info("onDestroy()");
        buttonStop.performClick();  // close port and stop "testingThread"
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

    private void runTest() {
        testingThread = new Thread(() -> {
            int pingsCounter = 0;
            while (!Thread.currentThread().isInterrupted()) {
                String pingResult = SuCommandsHelper.executeCmd("ping -c 1 -s 50000 192.168.1.1", 1000);  // without timeout may long for 10 seconds if ping fails
                pingsCounter++;
                final int pingsCounterFinal = pingsCounter;
                runOnUiThread(() -> {
                    if (pingResult.contains("1 received")) {
                        textViewTestResult.setText(String.format("Тест успешен (пингов: %d)", pingsCounterFinal));
                        textViewTestResult.setVisibility(View.VISIBLE);
                    } else if (testingThread.isAlive()) {
                        textViewTestResult.setTextColor(getResources().getColor(R.color.red_orange));
                        textViewTestResult.setText("Тест неудачен");
                        textViewTestResult.setVisibility(View.VISIBLE);
                        buttonStop.performClick();
                    }
                });
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        testingThread.start();
    }
}
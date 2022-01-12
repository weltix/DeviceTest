package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.su.SuCommandsHelper.CMD_PING;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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
    private ProgressBar progressBar;

    private int pingsCount = 10;
    private String pingAddress = "192.168.1.1";

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
                progressBar.setVisibility(View.VISIBLE);
                runTest();
                break;
            case R.id.button_stop:
                logger.info("onClick() button_stop");
                if (testingThread != null)
                    testingThread.interrupt();
                buttonStop.setEnabled(false);
                progressBar.setVisibility(View.INVISIBLE);
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
        progressBar = findViewById(R.id.progress_bar);

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

        buttonStart.performClick();

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
            while (!Thread.currentThread().isInterrupted() && pingsCounter < pingsCount) {
                String pingResult = SuCommandsHelper.executeCmd(CMD_PING + pingAddress, 0);  // without timeout may long for 10 seconds if ping fails (it is not because of -w 10, simply such behaviour)
                pingsCounter++;
                final int pingsCounterFinal = pingsCounter;
                if (pingResult.equals("OK")) {
                    runOnUiThread(() -> {
                        textViewTestResult.setText(String.format(getString(R.string.ethernet_ping_test_count), pingAddress, pingsCounterFinal));
                        textViewTestResult.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> {
                        textViewTestResult.setTextColor(getResources().getColor(R.color.red_orange));
                        textViewTestResult.setText(String.format(getString(R.string.ethernet_ping_test_fails), pingAddress, pingsCounterFinal));
                        textViewTestResult.setVisibility(View.VISIBLE);
                        buttonStop.performClick();
                    });
                    logger.error(String.format("Error when testing Ethernet; ping %d from %d", pingsCounterFinal, pingsCount));
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            runOnUiThread(() -> {
                textViewTestResult.setText(getString(R.string.ethernet_ping_test_successful));
                buttonStop.performClick();
            });
        });
        testingThread.start();
    }
}
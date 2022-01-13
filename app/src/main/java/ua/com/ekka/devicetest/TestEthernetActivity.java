package ua.com.ekka.devicetest;

import static ua.com.ekka.devicetest.MainActivity.pingIp;
import static ua.com.ekka.devicetest.MainActivity.pingsCount;
import static ua.com.ekka.devicetest.eth.ConnectivityReceiver.NETWORK_STATE_CHANGED;
import static ua.com.ekka.devicetest.su.SuCommandsHelper.CMD_PING;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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

    private EditText editTextPingIp;
    private EditText editTextPingNTimes;

    private Button buttonStart;
    private Button buttonStop;

    private TextView textViewTestResult;
    private ProgressBar progressBar;

    private Thread testingThread;

    private BroadcastReceiver networkStateChangedBrRv = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            displayNetworkParameters();
        }
    };

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

        editTextPingIp = findViewById(R.id.edittext_ping_ip);
        editTextPingIp.setSelectAllOnFocus(true);
        editTextPingIp.setText(pingIp);
        editTextPingIp.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {  // when "Готово" on keyboard is pressed
                    String inputText = editTextPingIp.getText().toString();
                    boolean isValidIP4Address = EthernetHelper.getInstance(TestEthernetActivity.this).isValidIP4Address(inputText);
                    if (isValidIP4Address)
                        pingIp = inputText;
                    else
                        editTextPingIp.setText(pingIp);
                    editTextPingIp.clearFocus();
                    InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        InputFilter[] inputFilterIPv4 = new InputFilter[1];
        inputFilterIPv4[0] = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                    if (!resultingTxt.matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
                        return "";
                    } else {
                        String[] splits = resultingTxt.split("\\.");
                        for (int i = 0; i < splits.length; i++) {
                            if (Integer.valueOf(splits[i]) > 255) {
                                return "";
                            }
                        }
                    }
                }
                return null;
            }
        };
        editTextPingIp.setFilters(inputFilterIPv4);  // inputFilterIPv4 IP v4 input

        editTextPingNTimes = findViewById(R.id.edittext_ping_n_times);
        editTextPingNTimes.setSelectAllOnFocus(true);
        editTextPingNTimes.setText(pingsCount + "");
        editTextPingNTimes.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {  // when "Готово" on keyboard is pressed
                String inputText = editTextPingNTimes.getText().toString();
                int inputValue;
                try {
                    inputValue = Integer.valueOf(inputText);  // here we are sure that inputText is only digits (inputFilterDigits ensures this)
                } catch (NumberFormatException e) {
                    inputValue = 0;
                }
                if (inputValue > 0)
                    pingsCount = inputValue;
                else
                    editTextPingNTimes.setText(String.valueOf(pingsCount));
                editTextPingNTimes.clearFocus();
                InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        InputFilter[] inputFilterDigits = new InputFilter[1];
        inputFilterDigits[0] = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                    if (!resultingTxt.matches("^[1-9]\\d*")) {
                        return "";  // "" replaces last edited symbol, that makes our string not matched (backspaces not counted even if after it we get wrong matching)
                    }
                }
                return null;
            }
        };
        editTextPingNTimes.setFilters(inputFilterDigits);

        textViewTestResult = findViewById(R.id.textview_test_result);
        progressBar = findViewById(R.id.progress_bar);

        buttonStart = findViewById(R.id.button_start);
        buttonStop = findViewById(R.id.button_stop);

        buttonStart.setOnClickListener(buttonClickListener);
        buttonStop.setOnClickListener(buttonClickListener);

        IntentFilter networkStateIntentFilter = new IntentFilter(NETWORK_STATE_CHANGED);
        this.registerReceiver(networkStateChangedBrRv, networkStateIntentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");
        displayNetworkParameters();
        buttonStart.performClick();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.info("onDestroy()");
        buttonStop.performClick();  // close port and stop "testingThread"
        this.unregisterReceiver(networkStateChangedBrRv);
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

    private void displayNetworkParameters() {
        EthernetHelper ethernetHelper = EthernetHelper.getInstance(this);
        IP_Settings ipSettings = ethernetHelper.ipSettings;
        logger.debug("displayNetworkParameters(), " + ipSettings.toString());

        TextView textViewIp = findViewById(R.id.textview_ip);
        TextView textViewMask = findViewById(R.id.textview_mask);
        TextView textViewGateway = findViewById(R.id.textview_gateway);
        textViewIp.setText(ipSettings.getIp());
        textViewMask.setText(ipSettings.getNetmask());
        textViewGateway.setText(ipSettings.getGateway());
    }

    private void runTest() {
        testingThread = new Thread(() -> {
            int pingsCounter = 0;
            while (!Thread.currentThread().isInterrupted() && pingsCounter < pingsCount) {
                String pingResult = SuCommandsHelper.executeCmd(CMD_PING + pingIp, 0);  // without timeout may long for 10 seconds if ping fails (it is not because of -w 10, simply such behaviour)
                pingsCounter++;
                final int pingsCounterFinal = pingsCounter;
                if (pingResult.equals("OK")) {
                    runOnUiThread(() -> {
                        textViewTestResult.setText(String.format(getString(R.string.ethernet_ping_test_count), pingIp, pingsCounterFinal));
                        textViewTestResult.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> {
                        textViewTestResult.setTextColor(getResources().getColor(R.color.red_orange));
                        textViewTestResult.setText(String.format(getString(R.string.ethernet_ping_test_fails), pingIp, pingsCounterFinal));
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
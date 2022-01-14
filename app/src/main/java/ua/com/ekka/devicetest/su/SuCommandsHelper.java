package ua.com.ekka.devicetest.su;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import ua.com.ekka.devicetest.BuildConfig;

public class SuCommandsHelper {

    private static final String TAG = "SU";

    public static final String CMD_SET_IMMERSIVE_MODE_ON = "settings put global policy_control immersive.full=" + BuildConfig.APPLICATION_ID;  // single command to get immersive mode without any confirmation dialogs and even without reboot (when systemui disabled, there are no any bars anyway) (not have effect in API19 aosp_drone2)
    public static final String CMD_SET_IMMERSIVE_MODE_OFF = "settings put global policy_control immersive.full=";                              // single command to reset immersive mode (not have effect in API19 aosp_drone2)

    public static final String CMD_USER_SETUP_COMPLETE_0 = "settings put secure user_setup_complete 0";  // inactivates navigation buttons "Home" (circle) and "Overview" (square); use with "settings put global policy_control immersive.full=ua.com.ekka.devicetest" to hide all system bars forever (not appears even on touch)
    public static final String CMD_USER_SETUP_COMPLETE_1 = "settings put secure user_setup_complete 1";  // activates navigation buttons "Home" (circle) and "Overview" (square); don't forget to reset "settings put global policy_control immersive.full=" to show all system bars and make they behaviour as it was originally

    public static final String CMD_PING = "ping -c 1 -w 10 ";  // simply concat IP address to this command (-c 1 means one command, -w 10 means wait 10s max)
    public static final String CMD_IPERF = "iperf -c 192.168.1.2 -t 10 -f -m";  // 192.168.1.2 - iperf server is set up there

    public static final String CMD_REBOOT_TO_BOOTLOADER = "reboot bootloader";

    private static final String CMD_BUSYBOX_WHOAMI = "busybox whoami";
    private static final String CMD_BUSYBOX_ID = "busybox id -u";
    private static boolean isRooted = false;

    private static Context mContext;

    public SuCommandsHelper(Context context) {
        this.mContext = context;
    }

    /**
     * Executes command using 'su' process. 'su' process not responses with "null" (EOF) when any
     * command finished to execute, rather it wait another command like in terminal. So we can get
     * only first bytes, that became available to read during timeout, that we specify in parameters.
     * If there will be another part of data, that will come separately after first read part, it
     * will be lost, because we already get out from reader.ready() method.
     * Without timeout may be situations when command executes even 30sec (abnormal), so sometimes
     * timeout is very useful.
     *
     * @param cmd
     * @param timeout if > 0 then we are waiting for answer only for a specified time, and return TIMEOUT if don't receive specific answer;
     *                if = 0 then we are waiting for answer till it will be received, and return only OK or ERROR.
     * @return TIMEOUT (if timeout exhausted), OK, ERROR or specific answer.
     */
    public static String executeCmd(String cmd, long timeout) {

        long stopTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        long startTime = System.nanoTime();
        String result = "";

        int cnt = 0;
        char[] buf = new char[1024];

        DataOutputStream outputStream = null;
        BufferedReader reader = null;
        try {
            Process su;
            Log.w(TAG, "BEGIN CMD: " + cmd);

            if (timeout > 0) {
                su = Runtime.getRuntime().exec("su");
                outputStream = new DataOutputStream(su.getOutputStream());
                reader = new BufferedReader(new InputStreamReader(su.getInputStream()));

                outputStream.writeBytes(cmd + "\n");//" && echo \"DONE\"
                outputStream.flush();

                while (!reader.ready()) {
                    if (stopTime <= System.nanoTime()) {
                        result = "TIMEOUT";
                        break;
                    }
                    Thread.currentThread().sleep(100);
                }

                while (reader.ready()) {
                    cnt = reader.read(buf, 0, buf.length);
                    result += new String(buf, 0, cnt);
                }
                outputStream.writeBytes("exit\n");
                outputStream.flush();
            } else {
                su = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                su.waitFor();
                result = (su.exitValue() == 0 ? "OK" : "ERROR");
            }

            Log.w(TAG, "END CMD:   " + cmd + "\nTIME:   " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms\nRESULT: " + result);

        } catch (IOException e) {
            Log.e(TAG, "executeCmd IOException Error: " + e.getMessage());
            result = "ERROR";
        } catch (InterruptedException e) {
            Log.e(TAG, "executeCmd InterruptedException Error: " + e.getMessage());
            result = "ERROR";
            Thread.currentThread().interrupt();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    result = "ERROR";
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    result = "ERROR";
                }
            }
        }
        return result;
    }

    /**
     * Method executes the specified program in separate native process.
     * Must be called only from thread other than UI thread, because may block calling thread by
     * BufferedReader.readLine() method for unpredictable period of time.
     * @param cmd the name of the program to execute
     * @return response of program
     */
    public static String executeCmdBlocking(String cmd) {
        boolean isMainThread = Thread.currentThread().equals(Looper.getMainLooper().getThread());
        if (isMainThread)
            return "executeCmdBlocking() method cannot be called from UI thread.";

        StringBuilder response = new StringBuilder();
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(cmd);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                Log.w(TAG, inputLine);
                response.append(inputLine);
            }
            in.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        return response.toString();
    }

    private static SetupRootCallback setupRootCallback;

    public interface SetupRootCallback {
        void onSetupRoot(final int result);
    }

    public void setSetupRootCallback(SetupRootCallback cback) {
        setupRootCallback = cback;
    }

    private void rootIsSetEvent(final int result) {
        setupRootCallback.onSetupRoot(result);
    }

    private boolean checkRoot() {
        boolean result = false;
        String id = SuCommandsHelper.executeCmd(CMD_BUSYBOX_ID, 2000).trim();
        String whoami = SuCommandsHelper.executeCmd(CMD_BUSYBOX_WHOAMI, 10000);  //10000
        if (whoami.contains("root") || id.equals("0")) {
            SuCommandsHelper.executeCmd("sync", 0);//2000
            result = true;
        } else {
            Log.e(TAG, "access root is NOT granted");
            result = false;
        }
        return result;
    }

    public void verifyRootRights() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                isRooted = false;
                do {
                    try {
                        //Log.d(TAG, "VerifyRootRights... ");
                        isRooted = checkRoot();
                        if (isRooted) {
                            Log.d(TAG, "verifyRootRights(), allowed");
                            rootIsSetEvent(1);
                        } else {
                            Log.d(TAG, "verifyRootRights(), not allowed");
                            rootIsSetEvent(0);
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (!isRooted);
            }
        });
        thread.start();
    }
}

package ua.com.ekka.devicetest.uart;

import static ua.com.ekka.devicetest.MainActivity.PRODUCT_AOSP_DRONE2;
import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_PX30;
import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_RK3399;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

/**
 * Handles work of single COM port at one time.
 */
public class UartWorker {

    Logger logger = Log4jHelper.getLogger(UartWorker.class.getName());

    public static final int EVENT_UART_OPEN = 0;
    public static final int EVENT_UART_CLOSED = 1;
    public static final int EVENT_UART_READ = 2;
    public static final int EVENT_UART_ERROR = 3;

    public static String[] SERIAL_PORTS;
    public static String[] SERIAL_PORTS_AOSP_DRONE2 = {"/dev/ttySAC1", "/dev/ttySAC2"};
    public static String[] SERIAL_PORTS_RES_PX30 = {"/dev/ttyS1", "/dev/ttyS3"};
    public static String[] SERIAL_PORTS_RES_RK3399 = {"/dev/ttyS0", "/dev/ttyS4"};

    public static int[] baudrates = {9600, 19200, 38400, 57600, 115200};

    private String openedUartName;   // used to keep info about currently opened port (e.g. for logging)
    private int openedUartBaudrate;  // used to keep info about currently opened port (e.g. for logging)

    private Handler mHandler;
    private SerialPort serialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;

    public static String UART_CHANGE_SETTINGS = "uart_change_settings";
    private Context context;

    public UartWorker(Handler handler, Context context) {
        mHandler = handler;
        IntentFilter intentFilter = new IntentFilter(UART_CHANGE_SETTINGS);
        context.registerReceiver(uartChangeSettings, intentFilter);
        switch (android.os.Build.PRODUCT) {
            case PRODUCT_AOSP_DRONE2:
                SERIAL_PORTS = SERIAL_PORTS_AOSP_DRONE2;
                break;
            case PRODUCT_RES_PX30:
                SERIAL_PORTS = SERIAL_PORTS_RES_PX30;
                SuCommandsHelper.executeCmd("echo 51 > /sys/class/gpio/export", 0);  // create GPIO(51) file for UART1_DTR_PIN_ (/dev/ttyS1)
                SuCommandsHelper.executeCmd("echo out > /sys/class/gpio/gpio51/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 50 > /sys/class/gpio/export", 0);  // create GPIO(50) file for UART1_DSR_PIN_ (/dev/ttyS1)
                SuCommandsHelper.executeCmd("echo in > /sys/class/gpio/gpio50/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 45 > /sys/class/gpio/export", 0);  // create GPIO(45) file for UART3_DTR_PIN_ (/dev/ttyS3)
                SuCommandsHelper.executeCmd("echo out > /sys/class/gpio/gpio45/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 44 > /sys/class/gpio/export", 0);  // create GPIO(44) file for UART3_DSR_PIN_ (/dev/ttyS3)
                SuCommandsHelper.executeCmd("echo in > /sys/class/gpio/gpio44/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio51/value", 0);  // set GPIO(51)/UART1_DTR_PIN_ to high level
//                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio50/value", 0);  // set GPIO(50)/UART1_DSR_PIN_ to high level
                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio45/value", 0);  // set GPIO(45)/UART3_DTR_PIN_ to high level
//                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio44/value", 0);  // set GPIO(44)/UART3_DSR_PIN_ to high level
                break;
            case PRODUCT_RES_RK3399:
                SERIAL_PORTS = SERIAL_PORTS_RES_RK3399;
                SuCommandsHelper.executeCmd("echo 83 > /sys/class/gpio/export", 0);  // create GPIO(83) file for UART0_DTR_PIN_ (/dev/ttyS0)
                SuCommandsHelper.executeCmd("echo out > /sys/class/gpio/gpio83/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 82 > /sys/class/gpio/export", 0);  // create GPIO(82) file for UART0_DSR_PIN_ (/dev/ttyS0)
                SuCommandsHelper.executeCmd("echo in > /sys/class/gpio/gpio82/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 4 > /sys/class/gpio/export", 0);  // create GPIO(4) file for UART4_DTR_PIN_ (/dev/ttyS4)
                SuCommandsHelper.executeCmd("echo out > /sys/class/gpio/gpio4/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 90 > /sys/class/gpio/export", 0);  // create GPIO(90) file for UART4_DSR_PIN_ (/dev/ttyS4)
                SuCommandsHelper.executeCmd("echo in > /sys/class/gpio/gpio90/direction", 0);  // set direction in special file
                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio83/value", 0);  // set GPIO(83)/UART0_DTR_PIN_ to high level
//                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio82/value", 0);  // set GPIO(82)/UART0_DSR_PIN_ to high level
                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio4/value", 0);  // set GPIO(4)/UART4_DTR_PIN_ to high level
//                SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio90/value", 0);  // set GPIO(90)/UART4_DSR_PIN_ to high level
                break;
            default:
                SERIAL_PORTS = SERIAL_PORTS_AOSP_DRONE2;
        }
    }

    /**
     * Opens serial port.
     *
     * @param uartName "/dev/ttySAC1", "/dev/ttySAC2", "/dev/ttyS1", "/dev/ttyS3" etc.
     * @param baudrate speed
     */
    public void openPort(String uartName, int baudrate) {
        if (android.os.Build.PRODUCT.equals(PRODUCT_RES_PX30)) {
            if (uartName.equals(SERIAL_PORTS_RES_PX30[0]))
                SuCommandsHelper.executeCmd("echo 0 > /sys/class/gpio/gpio51/value", 0);  // set GPIO(51)/UART1_DTR_PIN_ to ON state
            else if (uartName.equals(SERIAL_PORTS_RES_PX30[1]))
                SuCommandsHelper.executeCmd("echo 0 > /sys/class/gpio/gpio45/value", 0);  // set GPIO(45)/UART3_DTR_PIN_ to ON state
        } else if (android.os.Build.PRODUCT.equals(PRODUCT_RES_RK3399)) {
            if (uartName.equals(SERIAL_PORTS_RES_RK3399[0]))
                SuCommandsHelper.executeCmd("echo 0 > /sys/class/gpio/gpio83/value", 0);  // set GPIO(83)/UART0_DTR_PIN_ to ON state
            else if (uartName.equals(SERIAL_PORTS_RES_RK3399[1]))
                SuCommandsHelper.executeCmd("echo 0 > /sys/class/gpio/gpio4/value", 0);  // set GPIO(4)/UART4_DTR_PIN_ to ON state
        }

        try {
            serialPort = SerialPort
                    .newBuilder(new File(uartName), baudrate) // Serial address, baud rate
                    .parity(0)   // Parity bit; 0: No parity bit (NONE, default); 1: Odd parity bit (ODD); 2: Even parity bit (EVEN)
                    .dataBits(8) // Data bits, default 8; optional values are 5 ~ 8
                    .stopBits(1) // Stop bit, default 1; 1: 1 stop bit; 2: 2 stop bit
                    .flowCon(0)  // Flow control, 0: none; 1: RTS/CTS; 2: Xon/Xoff
                    .build();
            mOutputStream = serialPort.getOutputStream();
            mInputStream = serialPort.getInputStream();

            if (mReadThread != null)
                mReadThread.interrupt();
            mReadThread = new ReadThread();
            mReadThread.start();
            logger.warn(String.format("openPort(), %s (%d, 8, N, 1) opened successfully", uartName, baudrate));
            sendMsgToParent(EVENT_UART_OPEN, 0, new String[]{uartName, String.valueOf(baudrate)});
        } catch (IOException | SecurityException | NullPointerException e) {
            logger.error(String.format("openPort(), error opening %s (%d, 8, N, 1)", uartName, baudrate), e);
            sendMsgToParent(EVENT_UART_ERROR, 0, new String[]{uartName, String.valueOf(baudrate), "opening COM port"});
        }
        openedUartName = uartName;
        openedUartBaudrate = baudrate;
    }

    /**
     * Closes serial port.
     */
    public void closePort() {
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
            logger.warn(String.format("closePort(), %s (%d, 8, N, 1) opened successfully", openedUartName, openedUartBaudrate));
            sendMsgToParent(EVENT_UART_CLOSED, 0, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "closing COM port"});
        }
        if (android.os.Build.PRODUCT.equals(PRODUCT_RES_PX30)) {
            SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio51/value", 0);  // set GPIO(51)/UART1_DTR_PIN_ to OFF state
            SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio45/value", 0);  // set GPIO(45)/UART3_DTR_PIN_ to OFF state
        } else if (android.os.Build.PRODUCT.equals(PRODUCT_RES_RK3399)) {
            SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio83/value", 0);  // set GPIO(83)/UART0_DTR_PIN_ to OFF state
            SuCommandsHelper.executeCmd("echo 1 > /sys/class/gpio/gpio4/value", 0);   // set GPIO(4)/UART4_DTR_PIN_ to OFF state
        }
    }

    /**
     * Write bytes to opened COM port. To get bytes from string default charset is used (UTF-8).
     *
     * @param data string
     */
    public void sendData(String data) {
        if (serialPort != null) {
            if (mOutputStream != null) {
                try {
                    mOutputStream.write(data.getBytes(), 0, data.length());
                } catch (IOException e) {
                    sendMsgToParent(EVENT_UART_ERROR, 0, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "sending data to COM port"});
                    logger.error(String.format("sendData(), error sending data to %s (%d, 8, N, 1)", openedUartName, openedUartBaudrate), e);
                }
            }
        }
    }

    /**
     * Thread read data from COM port and send it to specified handler.
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            int size;
            while (!isInterrupted()) {
                try {
                    byte[] buffer = new byte[256];
                    size = mInputStream.read(buffer);
                    if (size > 0)
                        sendMsgToParent(EVENT_UART_READ, size, buffer);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendMsgToParent(EVENT_UART_ERROR, 0, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "receiving data from COM port"});
                    logger.error(String.format("ReadThread.run(), error reveiving data from %s (%d, 8, N, 1)", openedUartName, openedUartBaudrate), e);
                    return;
                }
            }
            logger.warn("ReadThread closed, " + this.toString());
        }
    }

    /**
     * Приемник сообщений об изменении параметров настройки порта
     */
    public BroadcastReceiver uartChangeSettings = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.debug("BroadcastReceiver [uartChangeSettings]");

//            if (intent.getAction().equals(UART_CHANGE_SETTINGS)) {
//                PrefValues prefValues = PrefWorker.getValues();
//                if (!getCoreNameUart(prefValues.uartName).equals(uartName)
//                        || prefValues.baudrate != baudrate
//                        || prefValues.flowControl != flowControl) {
//                    closeSerialPort();
//                    openPort(getCoreNameUart(prefValues.uartName), prefValues.baudrate, prefValues.flowControl, 0);
//                }
//            }
        }
    };

    /**
     * Проверка соответствия возможно доступных портов
     *
     * @param appNameUart
     * @return
     */
    public static String getCoreNameUart(String appNameUart) {

        String result = SERIAL_PORTS[0];
        int iPort = -1;
//        for (int i = 0; i < SERIAL_PORTS.length; i++) {
//            if (appNameUart.equals(DEF_UARTS[i])) {
//                iPort = i;
//                break;
//            }
//        }
        if (iPort >= 0) {
            result = SERIAL_PORTS[iPort];
        }
        return result;
    }

    /**
     * Send message to handler in MainActivity.
     *
     * @param msgCode 'what' code for type of message
     * @param arg     any desired int value
     * @param dataObj any object with any desired data
     */
    private void sendMsgToParent(int msgCode, int arg, Object dataObj) {
        mHandler.obtainMessage(msgCode, 1, arg, dataObj).sendToTarget();
    }
}

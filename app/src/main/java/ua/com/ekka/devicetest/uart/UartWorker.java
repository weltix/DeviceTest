package ua.com.ekka.devicetest.uart;

import static ua.com.ekka.devicetest.MainActivity.PRODUCT_AOSP_DRONE2;
import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_PX30;
import static ua.com.ekka.devicetest.MainActivity.PRODUCT_RES_RK3399;

import android.os.Build;
import android.os.Handler;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

/**
 * Handles work of single COM port at one time, but COM port may be changed.
 */
public class UartWorker {

    Logger logger = Log4jHelper.getLogger(UartWorker.class.getName());

    public static final int EVENT_UART_OPEN = 0;
    public static final int EVENT_UART_CLOSED = 1;
    public static final int EVENT_UART_READ = 2;
    public static final int EVENT_UART_ERROR = 3;

    public static final String OUT = "out";
    public static final String IN = "in";

    private String[] serialPorts;                 // all for current device
    private Map<String, List<Integer>> dtrsDsrs;  // all for every COM port of current device

    private static final String[] SERIAL_PORTS_AOSP_DRONE2 = {"/dev/ttySAC1", "/dev/ttySAC2"};

    private static final String[] SERIAL_PORTS_RES_PX30 = {"/dev/ttyS1", "/dev/ttyS3"};
    private static final Map<String, List<Integer>> DTR_DSR_RES_PX30;

    static {
        DTR_DSR_RES_PX30 = new HashMap<>();
        DTR_DSR_RES_PX30.put(SERIAL_PORTS_RES_PX30[0], new ArrayList<>(Arrays.asList(51, 50)));  // DTR and DSR for one port
        DTR_DSR_RES_PX30.put(SERIAL_PORTS_RES_PX30[1], new ArrayList<>(Arrays.asList(45, 44)));  // DTR and DSR for one port
    }

    private static final String[] SERIAL_PORTS_RES_RK3399 = {"/dev/ttyS0", "/dev/ttyS4"};
    private static final Map<String, List<Integer>> DTR_DSR_RES_RK3399;

    static {
        DTR_DSR_RES_RK3399 = new HashMap<>();
        DTR_DSR_RES_RK3399.put(SERIAL_PORTS_RES_RK3399[0], new ArrayList<>(Arrays.asList(1083, 1082)));  // DTR and DSR for one port
        DTR_DSR_RES_RK3399.put(SERIAL_PORTS_RES_RK3399[1], new ArrayList<>(Arrays.asList(1004, 1090)));  // DTR and DSR for one port
    }

    public static int[] baudrates = {9600, 19200, 38400, 57600, 115200};
    public static int[] baudratesUpDown = {9600, 19200, 38400, 57600, 115200, 57600, 38400, 19200, 9600};

    public int openedUartNumber;    // e.g. COM1, COM2 etc.
    public String openedUartName;   // used to keep info about currently opened port (e.g. for logging)
    public int openedUartBaudrate;  // used to keep info about currently opened port (e.g. for logging)

    private Handler mHandler;
    private SerialPort serialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;

    /**
     * Set {@link #serialPorts} for current device and tune GPIO contacts as DTR and DSR for
     * used COM ports.
     *
     * @param handler
     */
    public UartWorker(Handler handler) {
        mHandler = handler;
        switch (Build.PRODUCT) {
            case PRODUCT_AOSP_DRONE2:
                serialPorts = SERIAL_PORTS_AOSP_DRONE2;
                break;
            case PRODUCT_RES_PX30:
                serialPorts = SERIAL_PORTS_RES_PX30;
                dtrsDsrs = DTR_DSR_RES_PX30;
                for (String port : serialPorts)
                    initGpioContacts(port);
                break;
            case PRODUCT_RES_RK3399:
                serialPorts = SERIAL_PORTS_RES_RK3399;
                dtrsDsrs = DTR_DSR_RES_RK3399;
                for (String port : serialPorts)
                    initGpioContacts(port);
                break;
            default:
                break;
        }
    }

    /**
     * Set specified GPIO contacts as DTR and DSR lines for specified COM port.
     *
     * @param port
     */
    private void initGpioContacts(String port) {
        int gpioNum = dtrsDsrs.get(port).get(0);
        useGpioContact(gpioNum, OUT);               // as DTR
        setDTR(gpioNum, 0);                   // reset current GPIO contact to 0 as for DTR line of COM port according to RS-232 specification
        gpioNum = dtrsDsrs.get(port).get(1);
        useGpioContact(gpioNum, IN);                // as DSR
    }

    /**
     * Opens serial port.
     *
     * @param uartNum  1, 2, 3 etc. - means COM1, COM2, COM3 etc. Starts from 1.
     * @param baudrate speed
     */
    public void openPort(int uartNum, int baudrate) {
        closePort();  // initially we close previous port opened by this UartWorker

        String uartName = "";
        if (uartNum < 1)
            logger.error(String.format("openPort(), error opening COM with number %s", uartNum));
        else
            uartName = serialPorts[uartNum - 1];

        if (!Build.PRODUCT.equals(PRODUCT_AOSP_DRONE2)) {
            int gpioNum = dtrsDsrs.get(uartName).get(0);
            setDTR(gpioNum, 1);
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

            mReadThread = new ReadThread();
            mReadThread.start();

            logger.warn(String.format("openPort(), %s (%d, 8, N, 1) opened successfully (COM%d)", uartName, baudrate, uartNum));
            sendMsgToParent(EVENT_UART_OPEN, uartNum, new String[]{uartName, String.valueOf(baudrate)});
        } catch (IOException | SecurityException | NullPointerException e) {
            logger.error(String.format("openPort(), error opening %s (%d, 8, N, 1) (COM%d)", uartName, baudrate, uartNum), e);
            sendMsgToParent(EVENT_UART_ERROR, 0, uartNum, new String[]{uartName, String.valueOf(baudrate), "opening port COM" + uartNum});
        }
        openedUartNumber = uartNum;
        openedUartName = uartName;
        openedUartBaudrate = baudrate;
    }

    /**
     * Closes serial port.
     */
    public void closePort() {
        if (mReadThread != null)
            mReadThread.interrupt();
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
            logger.warn(String.format("closePort(), %s (%d, 8, N, 1) closed (COM%d)", openedUartName, openedUartBaudrate, openedUartNumber));
            sendMsgToParent(EVENT_UART_CLOSED, openedUartNumber, new String[]{openedUartName, String.valueOf(openedUartBaudrate)});

            if (!Build.PRODUCT.equals(PRODUCT_AOSP_DRONE2)) {
                for (List<Integer> dtrDsr : dtrsDsrs.values()) {
                    int gpioNum = dtrDsr.get(0);
                    setDTR(gpioNum, 0);
                }
            }
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
                    logger.error(String.format("sendData(), error sending data to %s (%d, 8, N, 1) (COM%d)", openedUartName, openedUartBaudrate, openedUartNumber), e);
                    sendMsgToParent(EVENT_UART_ERROR, 1, openedUartNumber, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "writing data to COM" + openedUartNumber});
                }
            }
        }
    }

    /**
     * Thread reads data from COM port and sends it to specified handler.
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            int size = 0;
            synchronized (UartWorker.this) {
                while (!isInterrupted()) {
                    try {
                        if (mInputStream == null || serialPort == null) {
                            sendMsgToParent(EVENT_UART_ERROR, 2, openedUartNumber, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "reading data from COM port"});
                            logger.error("ReadThread.run(), serialPort or it's mInputStream became null unexpectedly.");
                            return;
                        }
                        byte[] buffer = new byte[256];
                        if (mInputStream.available() > 0) {
                            size = mInputStream.read(buffer);
                            if (size > 0)
                                sendMsgToParent(EVENT_UART_READ, size, buffer);
                        }
                        Thread.sleep(10);
                    } catch (InterruptedException | IOException e0) {
                        logger.warn(String.format("ReadThread.run(), ReadThread=%s, error when read data from %s (%d, 8, N, 1), %s, everything OK - just port closed", this.toString(), openedUartName, openedUartBaudrate, e0));
                        return;
                    } catch (Exception e1) {
                        sendMsgToParent(EVENT_UART_ERROR, 3, openedUartNumber, new String[]{openedUartName, String.valueOf(openedUartBaudrate), "reading data from COM" + openedUartNumber});
                        logger.error(String.format("ReadThread.run(), ReadThread=%s, error when read data from %s (%d, 8, N, 1), %s", this.toString(), openedUartName, openedUartBaudrate, e1));
                        return;
                    }
                }
            }
            logger.warn(String.format("ReadThread closed, ReadThread=%s", this.toString()));
        }
    }

    /**
     * Overridden variant of {@link #sendMsgToParent(int, int, int, Object)}, that was left for
     * compatibility.
     *
     * @param msgCode 'what' code for type of message
     * @param arg2    any desired int value
     * @param dataObj any object with any desired data
     */
    private void sendMsgToParent(int msgCode, int arg2, Object dataObj) {
        sendMsgToParent(msgCode, 1, arg2, dataObj);
    }

    /**
     * Send message to handler in MainActivity.
     *
     * @param msgCode 'what' code for type of message
     * @param arg1    any desired int value
     * @param arg2    any desired int value
     * @param dataObj any object with any desired data
     */
    private void sendMsgToParent(int msgCode, int arg1, int arg2, Object dataObj) {
        mHandler.obtainMessage(msgCode, arg1, arg2, dataObj).sendToTarget();
    }

    /**
     * We use some GPIO contacts as DTR and DSR lines of our COM ports.
     *
     * @param gpioNum   GPIO contact's number
     * @param direction {@link #IN} or {@link #OUT}
     */
    private void useGpioContact(int gpioNum, String direction) {
        SuCommandsHelper.executeCmd(String.format("echo %d > /sys/class/gpio/export", gpioNum), 0);                       // create file for specified GPIO contact (e.g. "echo 51 > /sys/class/gpio/export")
        SuCommandsHelper.executeCmd(String.format("echo %s > /sys/class/gpio/gpio%d/direction", direction, gpioNum), 0);  // set GPIO contact as output or input (e.g. "echo out > /sys/class/gpio/gpio51/direction")
    }

    /**
     * Returns inverted value of GPIO contact. We use this contacts as COM port lines and they must
     * be inverted to adapt they state to RS-232 specification.
     *
     * @param gpioNum
     * @return inverted value
     */
    private int getGpioContactValue(int gpioNum) {
        String command = String.format("cat /sys/class/gpio/gpio%d/value", gpioNum);
        String response = SuCommandsHelper.executeCmd(command, 500);
        int result = -1;
        try {
            result = 1 - Integer.valueOf(response.trim());  // invert response
        } catch (NumberFormatException e) {
            logger.error("getGpioContactValue(int gpioNum)", e);
        }
        return result;
    }

    /**
     * DTR line is inverted (as of RS-232 specification), so we first prepare it (invert).
     *
     * @param gpioNum
     * @param value   value according to RS-232 specification:
     *                1 - this UART is ready, 0 - this UART is not ready.
     */
    private void setDTR(int gpioNum, int value) {
        int revertedValue = 1 - value;
        String command = String.format("echo %d > /sys/class/gpio/gpio%d/value", revertedValue, gpioNum);
        SuCommandsHelper.executeCmd(command, 0);
    }

    /**
     * Return DTR value for currently opened port.
     *
     * @return value for currently opened port according to RS-232 specification:
     * 1 - this UART is ready,
     * 0 - this UART is not ready,
     * -1 - not supported device.
     */
    public int getDTR() {
        if (Build.PRODUCT.equals(PRODUCT_AOSP_DRONE2))
            return -1;
        int gpioNum = dtrsDsrs.get(openedUartName).get(0);  // 0 means DTR line
        return getGpioContactValue(gpioNum);
    }

    /**
     * Return DSR value for currently opened port.
     *
     * @return value for currently opened port according to RS-232 specification:
     * 1 - connected device is ready,
     * 0 - connected device is not ready,
     * -1 - not supported device.
     */
    public int getDSR() {
        if (Build.PRODUCT.equals(PRODUCT_AOSP_DRONE2))
            return -1;
        int gpioNum = dtrsDsrs.get(openedUartName).get(1);  // 1 means DSR line
        return getGpioContactValue(gpioNum);
    }
}
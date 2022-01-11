package ua.com.ekka.devicetest.eth;

import static ua.com.ekka.devicetest.MainActivity.PRODUCT_AOSP_DRONE2;

import android.content.Context;
import android.os.Build;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

/**
 * Use "ifconfig" basically.
 * All commands here were tested on {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_AOSP_DRONE2}
 * <p>
 * {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_AOSP_DRONE2} and
 * {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_RES_PX30} both works perfectly with DHCP
 * server, even when cable reconnected physically.
 * But when DHCP server is not raised, only
 * {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_AOSP_DRONE2} works without reboot after
 * setting static IP, or when cable reconnected physically.
 * {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_RES_PX30} in such cases needs reboot.
 */
public class EthernetHelper {

    public static final String TAG = EthernetHelper.class.getSimpleName();
    private static Logger logger = Log4jHelper.getLogger(TAG);

    private Context mContext;
    public IP_Settings ipSettings = new IP_Settings();  // last received from system network parameters

    private static final String CMD_GET_IP_MASK = "ifconfig eth0";
    private static final String CMD_GET_GW = "ip route show";
    private static final String CMD_DELETE_ALL_GW = "ip route flush 0/0";
    private static final String CMD_ETH_UP = "ifconfig eth0 up";
    private static final String CMD_ETH_DOWN = "ifconfig eth0 down";
    private static final String CMD_DHCP_START = "ifconfig eth0 dhcp start";

    private static EthernetHelper ethernetHelper;

    public static synchronized EthernetHelper getInstance(Context context) {
        if (ethernetHelper == null) {
            if (Build.PRODUCT.equals(PRODUCT_AOSP_DRONE2)) {
                ethernetHelper = new EthernetHelper(context);
                logger.info("EthernetHelper created...");
            } else {
                ethernetHelper = new EthernetHelperNew(context);
                logger.info("EthernetHelperNew created...");
            }
        }
        return ethernetHelper;
    }

    public EthernetHelper(Context context) {
        mContext = context;
    }

    public static boolean isValidIP4Address(String ipAddress) {
        if (ipAddress.matches("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")) {
            String[] groups = ipAddress.split("\\.");

            for (int i = 0; i <= 3; i++) {
                String segment = groups[i];
                if (segment == null || segment.length() <= 0) {
                    return false;
                }

                int value = 0;
                try {
                    value = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (value > 255) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Получение настроек сети
     *
     * @return IP_Settings
     */
    public synchronized IP_Settings get_IP_MASK_GW() {
        IP_Settings settings = new IP_Settings();
        String tmpStr = SuCommandsHelper.executeCmd(CMD_GET_IP_MASK, 3000);

        if (tmpStr.contains("eth0: ip")) {
            int indexStart = tmpStr.indexOf("eth0: ip", 0) + "eth0: ip".length();
            int indexStop = tmpStr.indexOf("mask", indexStart);
            settings.setIp(tmpStr.substring(indexStart, indexStop).trim());
            indexStart = tmpStr.indexOf("mask", 0) + "mask".length();
            indexStop = tmpStr.indexOf("flags", indexStart);
            settings.setNetmask(tmpStr.substring(indexStart, indexStop).trim());
        }
        settings.setGateway(get_GW());
        logger.info("get_IP_MASK_GW(), " + settings.toString());
        return settings;
    }

    /**
     * Получение шлюза
     *
     * @return
     */
    synchronized String get_GW() {
        String tmpStr = SuCommandsHelper.executeCmd(CMD_GET_GW, 1000);
        String result = "";
        if (tmpStr.contains("default via")) {
            int indexStart = tmpStr.indexOf("default via", 0) + "default via".length();
            int indexStop = tmpStr.indexOf("dev eth0", indexStart);
            result = tmpStr.substring(indexStart, indexStop).trim();
        }
        return result;
    }
}



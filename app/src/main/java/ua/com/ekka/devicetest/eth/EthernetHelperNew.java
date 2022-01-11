package ua.com.ekka.devicetest.eth;

import android.content.Context;

import androidx.annotation.Nullable;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

/**
 * Use new "ip" command instead of "ifconfig".
 * All commands here were tested on {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_RES_PX30}
 * and {@link ua.com.ekka.devicetest.MainActivity#PRODUCT_RES_RK3399}
 */
public class EthernetHelperNew extends EthernetHelper {

    public static final String TAG = EthernetHelperNew.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    private static final String CMD_ETH_UP = "ip link set eth0 up";      // "ifconfig eth0 up" also works
    private static final String CMD_ETH_DOWN = "ip link set eth0 down";  // "ifconfig eth0 down" also works
    private static final String CMD_GET_IP_MASK = "ip addr show eth0";
    private static final String CMD_GET_GW = "ip route show";                  // returns default route of this device when DHCP absent in router, ALSO only this command returns route added by "ip route add default via xxx.xxx.xxx.xxx", sp it must be used first
    private static final String CMD_GET_GW_DHCP = "ip route show table eth0";  // returns default route of this device when DHCP is in router
    private static final String CMD_DELETE_ALL_GW = "ip route flush 0/0";      // "ip route del default" also may be used to delete default gateway

    public EthernetHelperNew(Context context) {
        super(context);
    }

    /**
     * Получение настроек сети.
     *
     * @return {@link IP_Settings}
     */
    @Override
    public synchronized IP_Settings get_IP_MASK_GW() {
        return get_IP_MASK_GW(null, null);
    }

    /**
     * Получение настроек сети.
     * Извлекает последний IP адрес из списка возвращённых адресов "inet' если statIP == null
     * и statMask == null, или же извлекает IP и netmask, переданные в аргументах, если таковые были
     * найдены в возращённом списке. Не null параметры решают проблему, когда уже добавлено
     * несколько адресов в список, и пользователь решил статически установить какой-либо адрес,
     * который уже есть в списке (был добавлен ранее), то есть не был добавлен последним.
     *
     * @return {@link IP_Settings}
     */
    public synchronized IP_Settings get_IP_MASK_GW(@Nullable String statIP, @Nullable String statMask) {
        IP_Settings settings = new IP_Settings();
        String ipAddrShowResponse = SuCommandsHelper.executeCmd(CMD_GET_IP_MASK, 3000);

        if (ipAddrShowResponse.contains("inet ") && statIP != null && statMask != null) {  // here we extract "inet" address specified in arguments, if it was found in "inet" addresses list (we expect it must be there exactly, because was add before)
            int indexStart = ipAddrShowResponse.indexOf("inet ") + "inet ".length();
            while (indexStart != 4) {  // -1 + "inet ".length() = 4
                int indexEnd = ipAddrShowResponse.indexOf(" ", indexStart);
                String ipAndMask = ipAddrShowResponse.substring(indexStart, indexEnd).trim();
                SubnetUtils.SubnetInfo subnetInfo = new SubnetUtils(ipAndMask).getInfo();
                String statCidrSignature = new SubnetUtils(statIP, statMask).getInfo().getCidrSignature();
                if (subnetInfo.getCidrSignature().equals(statCidrSignature)) {
                    settings.setIp(subnetInfo.getAddress());
                    settings.setNetmask(subnetInfo.getNetmask());
                    break;
                }
                indexStart = ipAddrShowResponse.indexOf("inet ", indexEnd) + "inet ".length();
            }
        } else if (ipAddrShowResponse.contains("inet ")) {  // here we extract last found "inet" address from returned list
            int indexStart = ipAddrShowResponse.lastIndexOf("inet ") + "inet ".length();
            int indexEnd = ipAddrShowResponse.indexOf(" ", indexStart);
            String ipAndMask = ipAddrShowResponse.substring(indexStart, indexEnd).trim();
            SubnetUtils.SubnetInfo subnetInfo = new SubnetUtils(ipAndMask).getInfo();
            settings.setIp(subnetInfo.getAddress());
            settings.setNetmask(subnetInfo.getNetmask());
        }
        settings.setGateway(get_GW());
        logger.info("get_IP_MASK_GW(), " + settings.toString());
        return settings;
    }

    /**
     * Получение шлюза (gateway).
     */
    @Override
    synchronized String get_GW() {
        String ipRouteShowResponseStatic = SuCommandsHelper.executeCmd(CMD_GET_GW, 1000);
        String ipRouteShowResponseDHCP = SuCommandsHelper.executeCmd(CMD_GET_GW_DHCP, 1000);
        String result = "";
        String ipRouteShowResponse = "";

        if (ipRouteShowResponseStatic.contains("default via"))  // this command must be used first (see description of command at start)
            ipRouteShowResponse = ipRouteShowResponseStatic;
        else if (ipRouteShowResponseDHCP.contains("default via"))
            ipRouteShowResponse = ipRouteShowResponseDHCP;
        else
            return result;

        int indexStart = ipRouteShowResponse.indexOf("default via") + "default via".length();
        int indexStop = ipRouteShowResponse.indexOf("dev eth0", indexStart);
        result = ipRouteShowResponse.substring(indexStart, indexStop).trim();
        return result;
    }
}
package ua.com.ekka.devicetest.eth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.os.Bundle;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;

public class ConnectivityReceiver extends BroadcastReceiver {

    private static final String TAG = ConnectivityReceiver.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    public static final String NETWORK_STATE_CHANGED = "ua.com.ekka.devicetest.NETWORK_STATE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        NetworkInfo info = (NetworkInfo) extras.getParcelable("networkInfo");
        logger.debug(info.toString());
        new Thread(() -> {
            EthernetHelper ethernetHelper = EthernetHelper.getInstance(context);
            ethernetHelper.ipSettings = ethernetHelper.get_IP_MASK_GW();
            IP_Settings ipSettings = ethernetHelper.ipSettings;
            logger.debug("onReceive(), " + ipSettings.toString());
            Intent intentEthernetParams = new Intent(NETWORK_STATE_CHANGED);
            intentEthernetParams.putExtra("ip", ipSettings.getIp());
            intentEthernetParams.putExtra("mask", ipSettings.getNetmask());
            intentEthernetParams.putExtra("gateway", ipSettings.getGateway());
            context.sendBroadcast(intentEthernetParams);
        }).start();
    }
}

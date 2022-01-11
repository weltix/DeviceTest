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

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        NetworkInfo info = (NetworkInfo) extras.getParcelable("networkInfo");
        NetworkInfo.State state = info.getState();
        logger.debug(info.toString() + " " + state.toString());
        new Thread(() -> {
            EthernetHelper ethernetHelper = EthernetHelper.getInstance(context);
            ethernetHelper.ipSettings = ethernetHelper.get_IP_MASK_GW();
            logger.debug(ethernetHelper.ipSettings.toString());
        }).start();
    }
}

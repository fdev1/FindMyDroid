package com.fernan.findmydroid;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.location.Location;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

/**
 * Sends SMS responses to commands from a background
 * service.
 */
public class SmsSender extends IntentService
{
    private static final String ACTION_SEND_LOC = "com.fernan.findmydroid.action.SEND_LOC";
    private static final String PARAM_RCPT = "com.fernan.findmydroid.extra.RCPT";

    public static void sendLocation(Context context, String rcpt)
    {
        Intent intent = new Intent(context, SmsSender.class);
        intent.setAction(ACTION_SEND_LOC);
        intent.putExtra(PARAM_RCPT, rcpt);
        context.startService(intent);
    }

    public SmsSender()
    {
        super("SmsSender");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SEND_LOC.equals(action))
            {
                final String param1 = intent.getStringExtra(PARAM_RCPT);
                sendLocation(param1);
            }
        }
    }

    private void sendLocation(String rcpt)
    {
        final SmsManager sms = SmsManager.getDefault();
        GoogleApiClient oGApi = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        if (oGApi.blockingConnect().isSuccess())
        {
            Location oLocation =
                    LocationServices.FusedLocationApi.getLastLocation(oGApi);
            if (oLocation != null)
            {
                String msgText = String.format("https://google.com/maps/place/%f,%f/@%f,%f,17z",
                        oLocation.getLatitude(), oLocation.getLongitude(),
                        oLocation.getLatitude(), oLocation.getLongitude());
                sms.sendTextMessage(rcpt, null, msgText, null, null);
                Log.d("SmsSender", "Sms Sent: " + msgText);
            }
            else
            {
                Log.d("SmsSender", "getLastLocation() failed");
            }
            oGApi.disconnect();
        }
        else
        {
            Log.d("SmsSender", "blockingConnect() failed");
        }
    }
}

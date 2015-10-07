package com.fernan.findmydroid;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Created by fernan on 9/29/15.
 */
public class SmsHandler extends BroadcastReceiver
{
    @SuppressLint("deprecation")
    public void onReceive(Context context, Intent intent)
    {
        final Bundle bundle = intent.getExtras();
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean findEnabled = sharedPref.getBoolean("pref_sms_enabled", false);
        final boolean trackEnabled = sharedPref.getBoolean("pref_sms_tracking", false);
        final String findMessage = sharedPref.getString("pref_sms_find_msg", "");
        final String trackMessage = sharedPref.getString("pref_sms_track_msg", "");

        Log.d("SmsHandler", "onReceive() called.");

        try
        {
            if (bundle != null)
            {
                final Object[] pdusObj = (Object[]) bundle.get("pdus");
                final String format = bundle.getString("format");

                Log.d("SmsHandler", "Format = " + format);
                Log.d("SmsHandler", "SDK: " + Build.VERSION.SDK_INT);

                if (pdusObj == null)
                    return;

                for (Object pduObj : pdusObj)
                {
                    // noinspection AndroidLintDeprecated, AndroidLintNewApi, deprecation
                    final SmsMessage msg = (Build.VERSION.SDK_INT >= 23) ?
                            SmsMessage.createFromPdu((byte[]) pduObj, format) :
                            SmsMessage.createFromPdu((byte[]) pduObj);
                    final String sender = msg.getDisplayOriginatingAddress();
                    final String text = msg.getDisplayMessageBody();

                    if (text.equals(findMessage))
                    {
                        if (findEnabled)
                        {
                            Log.d("SmsHandler", "Sms<" + sender + ">: " + text);
                            SmsSender.sendLocation(context, sender);
                        }
                    }
                    else if (trackEnabled && text.equals(trackMessage))
                    {
                        final StringBuilder cookie = new StringBuilder();
                        final int result = LocationTracker.startTracking(context, sender, cookie);
                        final SmsManager sms = SmsManager.getDefault();
                        if (result == LocationTracker.TRACKING_SUCCESS)
                        {
                            final String host = "http://fernando-rodriguez.github.io/FindMyDroid/";
                            final String resp = String.format("%s?cookie=%s",
                                    host, cookie.toString());
                            sms.sendTextMessage(sender, null, resp, null, null);
                            Log.d("SmsHandler", "Cookie: " + cookie.toString());
                            Log.d("SmsHandler", "Response: " + resp);
                        }
                        else if (result == LocationTracker.TRACKING_ERROR_NETWORK)
                        {
                            final StringBuilder resp = new StringBuilder();
                            resp.append("Could not start tracking due to a network error. ");

                            if (findEnabled)
                            {
                                resp.append("Please try the 'Find' message instead.");
                            }

                            sms.sendTextMessage(sender, null, resp.toString(), null, null);
                            Log.d("SmsHandler", "Network Error");
                            Log.d("SmsHandler", "Response Length: " + String.valueOf(resp.length()));
                        }
                        else
                        {
                            Log.d("SmsHandler", "Could not start tracking.");
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            Log.e("SmsHandler", e.toString());
        }
    }
}

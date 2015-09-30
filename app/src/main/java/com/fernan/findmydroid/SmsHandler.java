package com.fernan.findmydroid;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
        final String findMessage = sharedPref.getString("pref_sms_track_msg", "");

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
                    SmsMessage msg = (Build.VERSION.SDK_INT >= 23) ?
                            SmsMessage.createFromPdu((byte[]) pduObj, format) :
                            SmsMessage.createFromPdu((byte[]) pduObj);
                    String sender = msg.getDisplayOriginatingAddress();
                    String text = msg.getDisplayMessageBody();

                    if (text.equals(findMessage))
                    {
                        if (findEnabled)
                        {
                            Log.d("SmsHandler", "Sms<" + sender + ">: " + text);
                            SmsSender.sendLocation(context, sender);
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

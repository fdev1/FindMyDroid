package com.fernan.findmydroid;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class LocationTracker extends Service
{
    public static final int TRACKING_SUCCESS = 0;
    public static final int TRACKING_ERROR_NETWORK = 1;
    public static final int TRACKING_ERROR_UNKNOWN = 255;

    private static final String ACTION_START_TRACKING = "com.fernan.findmydroid.START_TRACKING";
    private static final String PARAM_COOKIE = "com.fernan.findmydroid.PARAM_COOKIE";
    private static final String PARAM_SENDER = "com.fernan.findmydroid.PARAM_SENDER";
    private GoogleApiClient oGApi;
    private SharedPreferences prefs;
    private String host;

    private static String invokeWebService(String url) throws IOException
    {
        final StringBuilder json = new StringBuilder();
        URLConnection urlConn;
        try
        {
            urlConn = new URL(url).openConnection();
            InputStream in = urlConn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null)
                json.append(line);
        }
        catch (IOException ex)
        {
            throw ex;
        }
        return json.toString();
    }

    public static int startTracking(Context context, String sender, final StringBuilder cookie)
    {
        class myInt
        {
            public int value = 0;
        }
        /*
         * Android does not allows making network requests from the
         * main thread, so to get around this and keep things simple
         * we perform the request from a new thread and join the thread
         * to wait for the result. There must be a better way to do
         * this.
         */
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("pref_track_server", "");
        final myInt i = new myInt();
        final Thread t = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    String url = String.format("%s?action=begin_session", host);
                    String resp = invokeWebService(url);
                    JSONObject oJson = new JSONObject(resp);
                    String result = oJson.getString("result");

                    Log.d("LocationTracker", "Response: " + resp);
                    Log.d("LocationTracker", "Result: " + result);

                    if (result.equals("OK"))
                    {
                        cookie.append(oJson.getString("cookie"));
                        i.value = TRACKING_SUCCESS;
                        Log.d("LocationTracker", "Cookie: " + oJson.getString("cookie"));
                    }
                    else
                    {
                        i.value = TRACKING_ERROR_UNKNOWN;
                        Log.d("LocationTracker", "Error: " + oJson.getString("reason"));
                    }
                }
                catch (IOException ex)
                {
                    i.value = TRACKING_ERROR_NETWORK;
                    cookie.delete(0, cookie.length() - 1);
                }
                catch (JSONException ex)
                {
                    Log.e("LocationTracker", "Invalid web service response.");
                    i.value = TRACKING_ERROR_UNKNOWN;
                    cookie.delete(0, cookie.length() - 1);
                }
            }
        });

        try
        {
            t.start();
            t.join();
        }
        catch (InterruptedException ex)
        {
            Log.d("LocationTracker", "startTracking() interrupted.");
            return TRACKING_ERROR_UNKNOWN;
        }

        if (i.value == TRACKING_SUCCESS)
        {
            Intent intent = new Intent(context, LocationTracker.class);
            intent.setAction(ACTION_START_TRACKING);
            intent.putExtra(PARAM_COOKIE, cookie.toString());
            intent.putExtra(PARAM_SENDER, sender);
            context.startService(intent);
        }

        return i.value;
    }

    @Override
    public void onCreate()
    {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        host = prefs.getString("pref_track_server", "");
        oGApi = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        oGApi.connect();
    }

    @Override
    public void onDestroy()
    {
        oGApi.disconnect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        HandlerThread t = new HandlerThread("LocationTrackerWorker",
                Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        Worker w = new Worker(t.getLooper());
        Message m = w.obtainMessage();
        m.arg1 = startId;
        m.setData(intent.getExtras());
        w.sendMessage(m);
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    private final class Worker extends Handler
    {
        public Worker(Looper looper)
        {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg)
        {
            final Bundle extras = msg.getData();
            final String cookie = extras.getString(PARAM_COOKIE);
            int exceptionCount = 0, networkErrorCount = 0;
            int iterInterval = 0;
            String url;
            JSONObject oJson;
            Location oLocation;

            final int interval =
                    Integer.valueOf(prefs.getString("pref_update_interval", "5")) * 1000;

            Log.d("LocationTracker", "Server: " + host);
            Log.d("LocationTracker", "Interval: " + String.valueOf(interval));
            Assert.assertNotNull(oGApi);

            while (true)
            {
                if (Thread.currentThread().isInterrupted())
                {
                    Log.e("LocationTracker", "Worker thread interrupted.");
                    return;
                }

                try
                {
                    Thread.sleep(iterInterval, 0);
                }
                catch (InterruptedException ex)
                {
                    Log.e("LocationTracker", "Worker thread interrupted");
                    return;
                }
                try
                {
                    if (!oGApi.blockingConnect().isSuccess())
                    {
                        Log.d("LocationTracker", "GoogleApiClient.blockingConnect() failed.");
                        iterInterval = 5 * 1000;
                        continue;
                    }

                    oLocation = LocationServices.FusedLocationApi.getLastLocation(oGApi);
                    url = String.format("%s?action=set_location&cookie=%s&latitude=%f&longitude=%f",
                            host, cookie, oLocation.getLatitude(), oLocation.getLongitude());
                    oJson = new JSONObject(invokeWebService(url));

                    if (oJson.getString("result").equals("OK"))
                    {
                        exceptionCount = networkErrorCount = 0;
                        Log.d("LocationTracker", "Location updated.");
                    }
                    else
                    {
                        Log.d("LocationTracker", "Session ended.");
                        stopSelf(msg.arg1);
                        return;
                    }
                }
                catch (IOException ex)
                {
                    Log.d("LocationTracker", "A network exception has occurred.");
                    /*
                     * After any IO exception we wait 10 seconds before continuing.
                     * After the 3rd IO exception we send an SMS notification to the
                     * user and sleep for 1 minute. After notifying the user 3 times
                     * in a row we just give up.
                     */
                    if (++exceptionCount == 3)
                    {
                        final SmsManager sms = SmsManager.getDefault();
                        final StringBuilder resp = new StringBuilder(
                                "A network error has occurred. " +
                                "I will retry again in 1 minute. " +
                                "If the error persist try using the 'Find' message. ");

                        if (++networkErrorCount == 3)
                            resp.append("This is the 3rd time. I'm giving up!");

                        sms.sendTextMessage(extras.getString(PARAM_SENDER, ""),
                                null, resp.toString(), null, null);

                        Log.d("LocationTracker", "User notified. SMS length: " + resp.length());
                        iterInterval = 60 * 1000;
                        exceptionCount = 0;

                        if (networkErrorCount == 3)
                        {
                            Log.d("LocationTracker", "Session ended.");
                            stopSelf(msg.arg1);
                            return;
                        }

                        continue;
                    }
                    else
                    {
                        iterInterval = 10 * 1000;
                        continue;
                    }
                }
                catch (JSONException jex)
                {
                    /*
                     * save from a bug on the web service, this should
                     * never happen, so we just log an error and exit
                     */
                    Log.e("LocationTracker", "Invalid response from web service");
                    Log.e("LocationTracker", "JSONException: " + jex.toString());
                    stopSelf(msg.arg1);
                    return;
                }
                iterInterval = interval;
            }
        }
    }
}

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
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import junit.framework.Assert;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class LocationTracker extends Service
{
    private static final String ACTION_START_TRACKING = "com.fernan.findmydroid.START_TRACKING";
    private static final String PARAM_COOKIE = "com.fernan.findmydroid.PARAM_COOKIE";
    private GoogleApiClient oGApi;
    private SharedPreferences prefs;
    private String host;

    private static String invokeWebService(String url)
    {
        StringBuilder json = new StringBuilder();
        URLConnection urlConn;
        try
        {
            urlConn = new URL(url).openConnection();
            InputStream in = urlConn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null)
            {
                json.append(line);
            }
        }
        catch (Exception ex)
        {
            Log.d("LocationTracker", "Exception: " + ex.toString());
            return "";
        }
        return json.toString();
    }

    public static String startTracking(Context context)
    {
        /*
         * Android does not allows making network requests from the
         * main thread, so to get around this and keep things simple
         * we perform the request from a new thread and join the thread
         * to wait for the result. There must be a better way to do
         * this.
         */
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("pref_track_server", "");
        final StringBuilder cookie = new StringBuilder();
        Thread t = new Thread(new Runnable()
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
                        Log.d("LocationTracker", "Cookie: " + oJson.getString("cookie"));
                    }
                    else
                    {
                        Log.d("LocationTracker", "Error: " + oJson.getString("reason"));
                    }
                }
                catch (Exception ex)
                {
                    Log.d("LocationTracker", ex.toString());
                }

            }
        });

        try
        {
            t.start();
            t.join();
        }
        catch (Exception ex)
        {
            Log.d("LocationTracker", "Exception (should not happen): " + ex.toString());
            return "";
        }

        Intent intent = new Intent(context, LocationTracker.class);
        intent.setAction(ACTION_START_TRACKING);
        intent.putExtra(PARAM_COOKIE, cookie.toString());
        context.startService(intent);

        return cookie.toString();
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
            Bundle extras = msg.getData();
            String cookie = extras.getString(PARAM_COOKIE);
            String url;
            JSONObject oJson;
            Location oLocation;

            final int interval = Integer.valueOf(prefs.getString("pref_update_interval", "5")) * 1000;

            Log.d("LocationTracker", "Server: " + host);
            Log.d("LocationTracker", "Interval: " + String.valueOf(interval));
            Assert.assertNotNull(oGApi);

            while (true)
            {
                try
                {
                    if (!oGApi.blockingConnect().isSuccess())
                    {
                        Log.d("LocationTracker", "GoogleApiClient.blockingConnect() failed.");
                        Thread.sleep(5000, 0);
                        continue;
                    }

                    oLocation = LocationServices.FusedLocationApi.getLastLocation(oGApi);
                    url = String.format("%s?action=set_location&cookie=%s&latitude=%f&longitude=%f",
                            host, cookie, oLocation.getLatitude(), oLocation.getLongitude());
                    oJson = new JSONObject(invokeWebService(url));

                    if (oJson.getString("result").equals("OK"))
                    {
                        Log.d("LocationTracker", "Location updated.");
                    }
                    else
                    {
                        Log.d("LocationTracker", "Session ended.");
                        stopSelf(msg.arg1);
                        return;
                    }
                    Thread.sleep(interval, 0);
                }
                catch (Exception ex)
                {
                    Log.d("LocationTracker", "Exception: " + ex.toString());
                    Log.d("LocationTracker", "Session ended due to exception.");
                    stopSelf(msg.arg1);
                    return;
                }
            }
        }
    }
}

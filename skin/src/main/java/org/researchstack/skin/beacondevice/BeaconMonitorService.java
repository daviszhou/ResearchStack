package org.researchstack.skin.beacondevice;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.researchstack.skin.beacondevice.BeaconStatus;
import org.researchstack.skin.beacondevice.ConfirmationReceiver;
import org.researchstack.skin.beacondevice.DBHelper;
import org.researchstack.skin.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class BeaconMonitorService extends Service implements BeaconConsumer, BootstrapNotifier {

    protected static final String TAG = "BeaconMonitorService";
    private static final long MINIMUM_EPISODE_DURATION = 60L * 1000L; //Beacon episode needs to be 60 seconds to be registered
    private static final long LOW_SCAN_TIME = 10L*1000L;
    private static final long LOW_SCAN_INTERVAL = 2L*60L*1000L;
    private static final long MEDIUM_SCAN_TIME = 2L*1000L;
    private static final long MEDIUM_SCAN_INTERVAL = 30L*1000L;
    private static final long HIGH_SCAN_TIME = 2L*1000L;
    private static final long HIGH_SCAN_INTERVAL = 0L;
    private int notificationId = 0;
    private HashMap<String, Boolean> mBeaconInRange = new HashMap<String, Boolean>();
    private BeaconManager mBeaconManager;
    private boolean debugMode = true;

    @Override
    public void onCreate()
    {
        Log.d(TAG, "BeaconMonitorService onCreate");
        super.onCreate();
    }

    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "BeaconMonitorService onStartCommand");

        mBeaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);
        mBeaconManager.getBeaconParsers().clear();

        mBeaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19")); //Modify for non Eddy Stone Beacons

        Log.d(TAG, "setting up background monitoring for beacons and power saving");
        Region region = new Region("backgroundRegion", null, null, null);
        RegionBootstrap regionBootstrap = new RegionBootstrap(this, region);

        if (debugMode) {
            setScanFrequency(HIGH_SCAN_TIME, HIGH_SCAN_INTERVAL);
        }

        //Ranging Functions
        mBeaconManager.bind(this);
        Log.d(TAG, "Binding activity manager to app");
        return START_STICKY; //Run service until explicitly stopped
    }

    //Ranging Functions
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mBeaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect()
    {
        Log.d(TAG, "Start tracking beacon distance");

        DBHelper dBHelper = new DBHelper(this);

        mBeaconManager.addRangeNotifier(new RangeNotifier()
        {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region)
            {
                if (beacons.size() > 0)
                {
                    int i = 0;
                    for (Beacon b : beacons)
                    {
                        i += 1;
                        String uid = b.getId1().toString();

                        if (i > mBeaconInRange.size())
                        { //new beacon is found
                            mBeaconInRange.put(uid, false); //put a new key-value pair in the hashmap
                        }
                        //boolean rangeStatusChanged = false;

                        Date time = Calendar.getInstance().getTime();

                        long dateTime = System.currentTimeMillis();

                        if (b.getDistance() < 1.0 && mBeaconInRange.get(uid) == false)
                        {
                            mBeaconInRange.put(uid, true);
                            BeaconStatus beaconStatus = new BeaconStatus(uid, mBeaconInRange.get(uid), dateTime, false); //Initially set userConfirmed to false
                            dBHelper.addBeaconStatus(beaconStatus);
                            //rangeStatusChanged = true;

                            setScanFrequency(HIGH_SCAN_TIME, HIGH_SCAN_INTERVAL);
                        }
                        else if (b.getDistance() > 1.0 && mBeaconInRange.get(uid) == true)
                        {

                            mBeaconInRange.put(uid, false);
                            BeaconStatus beaconStatus = new BeaconStatus(uid, mBeaconInRange.get(uid), dateTime, false); //Initially set userConfirmed to false
                            dBHelper.addBeaconStatus(beaconStatus);
                            //rangeStatusChanged = true;

                            try
                            {
                                BeaconStatus beaconStatusPrevious = dBHelper.getBeaconStatusReverseCount(2); //Careful with count
                                if (beaconStatusPrevious.isBeaconInRange())
                                {
                                    long startTime = beaconStatusPrevious.getDateTimeStamp();
                                    long endTime = dateTime;
                                    if (endTime - startTime > MINIMUM_EPISODE_DURATION)
                                    { // Prevents triggering unwanted notifications from walking past beacon
                                        sendNotification(String.valueOf(startTime), String.valueOf(endTime)); //TODO get notification user response and save beacon status based on response
                                    }
                                }
                            } catch (NullPointerException e) { }

                            setScanFrequency(MEDIUM_SCAN_TIME, MEDIUM_SCAN_INTERVAL);

                        }

                        //if (rangeStatusChanged) { //only store data if the range status has changed
                        //    BeaconStatus beaconStatus = new BeaconStatus(uid, mBeaconInRange.get(uid), dateTime, false); //Initially set userConfirmed to false
                        //    mDBHelper.addBeaconStatus(beaconStatus);
                        //}
                    }
                }
            }
        });

        try
        {
            mBeaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        }
        catch (RemoteException e) {   }
    }

    public BeaconStatus generateRandomBeaconStatus(boolean inRange)
    {
        long currentDateTime = System.currentTimeMillis();
        long lowerRange = System.currentTimeMillis()-30L*24L*60L*60L*1000L;

        Random r = new Random();
        long datetime =  + (long)(r.nextDouble()*(currentDateTime - lowerRange));

        return new BeaconStatus("testuid", inRange, datetime, true);
    }

    @Override
    public IBinder onBind(Intent intent) { //Required method for service
        return null;
    }

    @Override
    public void didEnterRegion(Region arg0)
    {
        Log.d(TAG, "did enter region.");

        setScanFrequency(MEDIUM_SCAN_TIME, MEDIUM_SCAN_INTERVAL);
    }

    @Override
    public void didExitRegion(Region region)
    {
        DBHelper dBHelper = new DBHelper(this);

        BeaconStatus beaconStatus = dBHelper.getBeaconStatusReverseCount(1);
        if (beaconStatus.isBeaconInRange())
        {
            // Add out of range beacon status
            String uid = beaconStatus.getUID();
            Long dateTime = System.currentTimeMillis();
            BeaconStatus newBeaconStatus = new BeaconStatus(uid, false, dateTime, false);

            dBHelper.addBeaconStatus(newBeaconStatus);

            try
            {
                BeaconStatus beaconStatusPrevious = dBHelper.getBeaconStatusReverseCount(2); //Careful with count
                if (beaconStatusPrevious.isBeaconInRange())
                {
                    long startTime = beaconStatusPrevious.getDateTimeStamp();
                    long endTime = dateTime;
                    if (endTime - startTime > MINIMUM_EPISODE_DURATION)
                    { // Prevents triggering unwanted notifications from walking past beacon
                        sendNotification(String.valueOf(startTime), String.valueOf(endTime)); //TODO check if notification is incrementing with current return scheme
                    }
                }
            }
            catch (NullPointerException e) { }

            // Set beacon as out of range
            String uID = beaconStatus.getUID();
            mBeaconInRange.put(uID, false);
        }

        setScanFrequency(LOW_SCAN_TIME, LOW_SCAN_INTERVAL);
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region)
    {
        //TODO compare didDetermineStateForRegion time delay versus didExitRegion
    }

    public int sendNotification(String startdatetime, String enddatetime)
    {
        //Process datetime
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("h:mm aa", Locale.US);

        calendar.setTimeInMillis(Long.parseLong(startdatetime));
        String startdatetimestring = format.format(calendar.getTime());

        calendar.setTimeInMillis(Long.parseLong(enddatetime));
        String enddatetimestring = format.format(calendar.getTime());

        //Intent that runs when "Yes" button is clicked
        Intent yesButtonIntent = new Intent(this, ConfirmationReceiver.class);
        yesButtonIntent.putExtra("startDateTime", startdatetime);
        yesButtonIntent.putExtra("endDateTime", enddatetime);
        yesButtonIntent.putExtra("userConfirmed", true);
        yesButtonIntent.putExtra("notificationId", notificationId);
        yesButtonIntent.setAction("yesButtonAction " + String.valueOf(notificationId)); //Generate unique action to prompt android to create new PendingIntent
        PendingIntent yesPendingIntent = PendingIntent.getBroadcast(this, 0, yesButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        //Intent that runs when "No" button is clicked
        Intent noButtonIntent = new Intent(this, ConfirmationReceiver.class);
        noButtonIntent.putExtra("userConfirmed", false);
        noButtonIntent.putExtra("notificationId", notificationId);
        noButtonIntent.setAction("noButtonAction " + String.valueOf(notificationId)); //Generate unique action to prompt android to create new PendingIntent
        PendingIntent noPendingIntent = PendingIntent.getBroadcast(this, 0, noButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent bodyPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        if (android.os.Build.VERSION.SDK_INT > 20) {
            NotificationCompat.Action yesAction =
                    new NotificationCompat.Action.Builder(R.drawable.ic_check_black_24dp, "Yes", yesPendingIntent).build(); //TODO add resources
            NotificationCompat.Action noAction =
                    new NotificationCompat.Action.Builder(R.drawable.ic_close_black_24dp, "No", noPendingIntent).build();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentTitle("Bathroom Tracker")
                    .setContentText("Were you pooping " + startdatetimestring + " to " + enddatetimestring + "?")
                    .setSmallIcon(R.drawable.ic_report_problem_black_24dp)
                    .setVibrate(new long[]{100, 200, 100, 200})
                    .addAction(yesAction)
                    .addAction(noAction);
            builder.setContentIntent(bodyPendingIntent);

            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(notificationId, builder.build());

        }
        else
        {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this)
                            .setContentTitle("Bathroom Tracker")
                            .setContentText("Were you pooping " + startdatetimestring + " to " + enddatetimestring + "?")
                            .setSmallIcon(R.drawable.ic_report_problem_black_24dp)
                            .setVibrate(new long[]{100, 200, 100, 200})
                            .addAction(R.drawable.ic_check_black_24dp, "Yes", yesPendingIntent)
                            .addAction(R.drawable.ic_close_black_24dp, "No", noPendingIntent);
            builder.setContentIntent(bodyPendingIntent);

            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            Log.d("BeaconMonitorService: ", "Notification ID: " + String.valueOf(notificationId));
            notificationManager.notify(notificationId, builder.build());
        }

        //Increment and persist the notification id
        Log.d(TAG, "Notification ID is set to " + notificationId);
        return notificationId++;
    }

    public void setScanFrequency(long scanTime, long intervalBetweenScanTime)
    {
        Log.d(TAG, "Scanning is set to " + String.valueOf(scanTime) + " ms with " +
                intervalBetweenScanTime + " ms rest between scans.");

        mBeaconManager.setBackgroundScanPeriod(scanTime);
        mBeaconManager.setBackgroundBetweenScanPeriod(intervalBetweenScanTime);

        try
        {
            mBeaconManager.updateScanPeriods();
        }
        catch (RemoteException e)
        {
            Log.d(TAG, "Error changing scan frequency");
        }
    }
}
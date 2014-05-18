package se.martinlarka.autoraft.app;

import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class AutoPilotService extends Service implements
        LocationListener,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {

    private static final int TAILSIZE = 10;
    public static final float SEARCH_WIDTH = 60;
    private Messenger gpsMessenger;
    private Messenger serialMessenger;
    private boolean isNavigating = false;

    // A request to connect to Location Services
    private LocationRequest mLocationRequest;

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;

    /*
    * Note if updates have been turned on. Starts out as "false"; is set to "true" in the
    * method handleRequestSuccess of LocationUpdateReceiver.
    */
    boolean mUpdatesRequested = false;
    private Location raftLocation;
    private Location previousRaftLocation = null;
    private ArrayList<LatLng> wayPoints = new ArrayList<LatLng>();
    private ArrayList<LatLng> raftTail = new ArrayList<LatLng>();
    private int currentDest = 0;
    private boolean autoPilotOn = true;

    public AutoPilotService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (startId == 1) { // FIXME Get start mode instead of just startId == 1
            if (extras != null) {
                gpsMessenger = (Messenger) extras.get(AutoRaft.GPSMESSENGER);
                serialMessenger = (Messenger) extras.get(AutoRaft.SERIALMESSENGER);
            } else {
                gpsMessenger = null;
                serialMessenger = null;
            }
            setupLocationClient();
            mLocationClient.connect();

            Toast.makeText(this, R.string.auto_pilot_started, Toast.LENGTH_LONG).show();
        } else {
            Bundle bundle = intent.getParcelableExtra(AutoRaft.WAYPOINTBUNDLE);
            if (bundle != null) {
                wayPoints = bundle.getParcelableArrayList(AutoRaft.WAYPOINTLIST);
                currentDest = bundle.getInt(AutoRaft.CURRENT_DEST);
            }
            else {
                bundle = intent.getParcelableExtra(AutoRaft.AUTOPILOTON);
                if (bundle != null)
                    autoPilotOn = bundle.getBoolean(AutoRaft.AUTOPILOTON);
            }
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mUpdatesRequested = false;
        // If the client is connected
        if (mLocationClient.isConnected()) {
            stopPeriodicUpdates();
        }

        // After disconnect() is called, the client is considered "dead".
        mLocationClient.disconnect();
        Toast.makeText(this, R.string.auto_pilot_stopped, Toast.LENGTH_LONG).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        raftLocation = location;

        // Send long, lat heading m.m to activity.
        Message msg = Message.obtain(null, AutoRaft.MESSAGE_LOCATION_CHANGED);
        Bundle bundle = new Bundle();
        bundle.putFloat(AutoRaft.BEARING, getRaftBearing());
        bundle.putDouble(AutoRaft.LONG, location.getLongitude());
        bundle.putDouble(AutoRaft.LAT, location.getLatitude());
        bundle.putFloat(AutoRaft.SPEED, location.getSpeed());

        // Get destination
        currentDest = getCurrentDest();
        bundle.putInt(AutoRaft.CURRENT_DEST, currentDest);
        if (previousRaftLocation != null && raftLocation.distanceTo(previousRaftLocation) > 10) {
            previousRaftLocation = raftLocation;
        }

        // Calculate new direction
        if (wayPoints.size() > 0) {
            bundle.putFloat(AutoRaft.BEARING_TO_DEST, bearingToDestination(currentDest));
            bundle.putFloat(AutoRaft.ANGLE_TO_DEST, angleFromHeading(wayPoints.get(currentDest)));
            if (autoPilotOn) sendAngleToSerial(angleFromHeading(wayPoints.get(currentDest)));
        }
        else {
            bundle.putFloat(AutoRaft.BEARING_TO_DEST, 0);
            bundle.putFloat(AutoRaft.ANGLE_TO_DEST, 0);
        }
        msg.setData(bundle);
        try {
            gpsMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(getClass().getName(), "Exception sending message");
        }
    }

    private float getRaftBearing() {
        float raftBearing = raftLocation.getBearing();
        if ( raftBearing < 0.01 ) {
            // Compute bearing from last pos

        } else if (raftBearing > 180) {
            // Remapp bearing to -180 to 180
            return raftBearing - 360;
        }
        return raftBearing;
    }

    private void sendAngleToSerial(float v) {
        Message msg = Message.obtain(null, AutoRaft.MESSAGE_LOCATION_CHANGED);
        Bundle bundle = new Bundle();
        bundle.putFloat(AutoRaft.ANGLE_TO_DEST, v);
        msg.setData(bundle);
        try {
            serialMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(getClass().getName(), "Exception sending message");
        }
    }

    private int getCurrentDest() {
        if ( wayPoints.size() < 2 ) return 0;

        Location tempLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        tempLocation.setLatitude(wayPoints.get(currentDest).latitude);
        tempLocation.setLongitude(wayPoints.get(currentDest).longitude);

        // If destination gets outside of search width find new destination
        if (Math.abs(angleFromHeading(tempLocation)) > AutoPilotService.SEARCH_WIDTH) {
            // Current dest is first dest. return next dest
            if (currentDest == 0) return 1;
            // Current dest is last dest. return next dest
            if (currentDest == wayPoints.size() - 1) return wayPoints.size() - 2;

            // If angle to heading after destination is smaller
            if ( Math.abs(angleFromHeading(new LatLng(wayPoints.get(currentDest - 1).latitude, wayPoints.get(currentDest - 1).longitude))) > Math.abs(angleFromHeading(new LatLng(wayPoints.get(currentDest + 1).latitude, wayPoints.get(currentDest + 1).longitude))) ) {
                return currentDest + 1;
            } else {
                return currentDest - 1;
            }
        }
        // As long as current destination is inside search width return current destination
        return currentDest;
    }

    private void setupLocationClient() {
        // Create a new global location parameters object
        mLocationRequest = LocationRequest.create();

        /*
         * Set the update interval
         */
        mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);

        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Set the interval ceiling to one minute
        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        // Note that location updates are off until the user turns them on
        mUpdatesRequested = true;

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mUpdatesRequested) {
            startPeriodicUpdates();
        }
    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
 /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         * connectionResult.hasResolution()
         */
        if (false) {
            try {

                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(null,LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */

            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        }
        else {
            // If no resolution is available, display a dialog to the user with the error.
            Log.d(LocationUtils.APPTAG, "Connection failed");
        }
    }

    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d(LocationUtils.APPTAG, getString(R.string.play_services_available));

            // Continue
            return true;
            // Google Play services was not available for some reason
        } else {
            // Display an error dialog
            Log.d(LocationUtils.APPTAG, "Google service not available");
            return false;
        }
    }

    /**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private void startPeriodicUpdates() {
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private void stopPeriodicUpdates() {
        mLocationClient.removeLocationUpdates(this);
    }

    private float angleFromHeading(Location destinaionLocation) {
        return raftLocation.bearingTo(destinaionLocation) - raftLocation.getBearing(); // ???
    }

    private float angleFromHeading(LatLng destinaionLocation) {
        Location destLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        destLocation.setLatitude(destinaionLocation.latitude);
        destLocation.setLongitude(destinaionLocation.longitude);
        return raftLocation.bearingTo(destLocation) - raftLocation.getBearing();
    }

    private float bearingToDestination(int dest) {
        Location destLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        destLocation.setLatitude(wayPoints.get(dest).latitude);
        destLocation.setLongitude(wayPoints.get(dest).longitude);
        return raftLocation.bearingTo(destLocation);
    }

    static public LatLng newPosition(double lat, double lng, double brng, double distance) {
        double dist = distance/6371.0;
        double latitude = Math.toRadians(lat);
        double longitude = Math.toRadians(lng);
        double bearing = Math.toRadians(brng);
        double lat2 = Math.asin( Math.sin(latitude)*Math.cos(dist) + Math.cos(latitude)*Math.sin(dist)*Math.cos(bearing) );
        double a = Math.atan2(Math.sin(bearing)*Math.sin(dist)*Math.cos(latitude), Math.cos(dist)-Math.sin(latitude)*Math.sin(lat2));

        return new LatLng(Math.toDegrees(lat2), Math.toDegrees(longitude + a));
    }
}

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
    private Messenger mMessenger;
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
    private Location previousRaftLocation;
    private Location headingLocation;
    private LatLng headingLatLng;
    private float raftAzimuth = 0;
    private ArrayList<LatLng> wayPoints = new ArrayList<LatLng>();
    private ArrayList<LatLng> raftTail = new ArrayList<LatLng>();
    private int currentDest = 0;
    private float filterLevel = 1;

    public AutoPilotService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (startId == 1) { // FIXME Get start mode instead of just startId == 1
            if (extras != null) {
                mMessenger = (Messenger) extras.get("MESSENGER");
            } else {
                mMessenger = null;
            }
            setupLocationClient();
            mLocationClient.connect();

            Toast.makeText(this, R.string.auto_pilot_started, Toast.LENGTH_LONG).show();
        } else {
            Bundle bundle = intent.getParcelableExtra(AutoRaft.WAYPOINTBUNDLE);
            if (bundle != null)
                wayPoints = bundle.getParcelableArrayList(AutoRaft.WAYPOINTLIST);
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

        previousRaftLocation = raftLocation;
        raftLocation = location;

        if ( previousRaftLocation != null ) raftAzimuth += (previousRaftLocation.bearingTo(raftLocation) - raftAzimuth) / filterLevel;

        headingLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        headingLatLng = newPosition(location.getLatitude(), location.getLongitude(), raftAzimuth, 0.1);
        headingLocation.setLatitude(headingLatLng.latitude);
        headingLocation.setLongitude(headingLatLng.longitude);

        // Send long, lat heading m.m to activity.
        Message msg = Message.obtain(null, AutoRaft.MESSAGE_LOCATION_CHANGED);
        Bundle bundle = new Bundle();
        bundle.putFloat(AutoRaft.BEARING, raftAzimuth);
        bundle.putDouble(AutoRaft.LONG, location.getLongitude());
        bundle.putDouble(AutoRaft.LAT, location.getLatitude());
        bundle.putFloat(AutoRaft.SPEED, location.getSpeed());

        bundle.putDouble(AutoRaft.HEADING_LONG, headingLocation.getLongitude());
        bundle.putDouble(AutoRaft.HEADING_LAT, headingLocation.getLatitude());

        // Get destination
        currentDest = getCurrentDest();
        bundle.putInt(AutoRaft.CURRENT_DEST, currentDest);

        // Calculate new direction
        //bundle.putFloat(AutoRaft.BEARING_TO_DEST, angleTo(destLocation, headingLocation));
        if (wayPoints.size() > 0) {
            bundle.putFloat(AutoRaft.BEARING_TO_DEST, bearingToDestination(currentDest));
            bundle.putFloat(AutoRaft.ANGLE_TO_DEST, angleFromHeading(wayPoints.get(currentDest)));
        }
        else {
            bundle.putFloat(AutoRaft.BEARING_TO_DEST, 0); // FIXME Maybe not 0???
            bundle.putFloat(AutoRaft.ANGLE_TO_DEST, 0);
        }
        msg.setData(bundle);
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(getClass().getName(), "Exception sending message");
        }
        Log.d("AutopilotService", "Loaction changed");
    }

    private int getCurrentDest() { //
        LatLng raftLatLng = new LatLng(raftLocation.getLatitude(), raftLocation.getLongitude());
        float minDistance = -1;
        int iMin = currentDest;
        for (int i=iMin; i < wayPoints.size(); i++) {
            float dist = distanceBetween(raftLatLng, wayPoints.get(i));
            if ( dist < minDistance || minDistance == -1 ) {
                Location tempLocation = new Location(LocationManager.PASSIVE_PROVIDER);
                tempLocation.setLatitude(wayPoints.get(i).latitude);
                tempLocation.setLongitude(wayPoints.get(i).longitude);
                if ( Math.abs(angleFromHeading(tempLocation)) < AutoPilotService.SEARCH_WIDTH ) {
                    minDistance = dist;
                    iMin = i;
                }
            }
        }
        return iMin;
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
        return raftLocation.bearingTo(destinaionLocation) - raftAzimuth; // ???
    }

    private float angleFromHeading(LatLng destinaionLocation) {
        Location destLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        destLocation.setLatitude(destinaionLocation.latitude);
        destLocation.setLongitude(destinaionLocation.longitude);
        return raftLocation.bearingTo(destLocation) - raftAzimuth;
    }

    private float bearingToDestination(int dest) {
        Location destLocation = new Location(LocationManager.PASSIVE_PROVIDER);
        destLocation.setLatitude(wayPoints.get(dest).latitude);
        destLocation.setLongitude(wayPoints.get(dest).longitude);
        return raftLocation.bearingTo(destLocation);
    }

    private float distanceBetween(LatLng point1, LatLng point2) {
        double earthRadius = 3958.75;
        double latDiff = Math.toRadians(point2.latitude-point1.latitude);
        double lngDiff = Math.toRadians(point2.longitude-point1.longitude);
        double a = Math.sin(latDiff /2) * Math.sin(latDiff /2) +
                Math.cos(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) *
                        Math.sin(lngDiff /2) * Math.sin(lngDiff /2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = earthRadius * c;

        int meterConversion = 1609;

        return new Float(distance * meterConversion).floatValue();
    }

    private float getHeading() {
        if ( previousRaftLocation != null ) {
            return previousRaftLocation.bearingTo(raftLocation);
        }
        return 0;
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

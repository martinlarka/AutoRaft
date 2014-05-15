package se.martinlarka.autoraft.app;

import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Messenger;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

public class AutoRaft extends Activity {

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final int REQUEST_WAYPOINT = 3;

    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Message types sent from AutoPilotService Handler
    public static final int MESSAGE_STATE = 1;
    public static final int MESSAGE_LOCATION_CHANGED = 2;
    public static final String BEARING = "autoraft_bearing";
    public static final String LONG = "autoraft_longtitude";
    public static final String LAT = "autoraft_latitude";
    public static final String SPEED = "autoraft_speed";
    public static final String CURRENT_DEST = "CURRENT_DEST";
    public static final String WAYPOINTLIST = "waypointlist";
    public static final String WAYPOINTBUNDLE = "WAYPOINTBUNDLE";
    public static final String BEARING_TO_DEST = "BEARINGTODEST";
    public static final String ANGLE_TO_DEST = "ANGLETODEST";
    public static final String SERIALMESSENGER = "SERIALMESSENGER";
    public static final String GPSMESSENGER = "GPSMESSENGER";
    public static final String AUTOPILOTON = "AUTOPILOTON";
    public static final String EXTRA_ROUTE_NUMBER = "ROUTENUMBER";

    private static TextView mTitle;
    private static TextView headingSeekBarValue;
    private static TextView textview1;
    private static TextView textview2;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private BluetoothAdapter mBluetoothAdapter = null;

    private static BluetoothSerialService mSerialService = null;

    private MenuItem mMenuItemConnect;
    private MenuItem mMenuItemAutoPilot;
    private MenuItem mMenuItemWaypointList;

    // Google Map
    private GoogleMap googleMap;
    private LatLng raftPos = null;
    private LatLng previousRaftPos = null;
    private double raftPosLat;
    private double raftPosLong;
    private float raftBearing;
    private float raftSpeed;
    private float bearingToDest;
    private float angleToDest;
    private boolean focusOnPosition = true;
    private boolean autoPilotOn = false;
    private int currentDest = 0;
    private boolean mapLocked = false;

    private static final double OFFSET = 0.1;

    private ArrayList<Marker> wayPoints = new ArrayList<Marker>();
    private ArrayList<Polyline> wayPointLines = new ArrayList<Polyline>();
    private ArrayList<Polyline> raftTrail = new ArrayList<Polyline>();

    // FIXME TEMP
    Polyline headingLine = null;
    Polyline destinationLine = null;
    Polyline searchLines = null;

    static final CameraPosition UMEA =
            new CameraPosition.Builder().target(new LatLng(63.83279, 20.26622))
                    .zoom(10)
                    .bearing(0)
                    .tilt(0)
                    .build();


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getBaseContext(), AutoPilotService.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_raft);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.device);
        mTitle.setText(R.string.title_not_connected);

        headingSeekBarValue = (TextView) findViewById(R.id.seek_bar_value);
        textview1 = (TextView) findViewById(R.id.textview1);
        textview2 = (TextView) findViewById(R.id.textview2);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            //finishDialogNoBluetooth();
            return;
        }

        mSerialService = new BluetoothSerialService(this, mHandlerBT);

        try {
            // Loading map
            initilizeMap();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Start auto pilot service for gps readings
        Intent intent = new Intent(this, AutoPilotService.class);
        Messenger gpsMessenger = new Messenger(mAutoPilotHandler);
        intent.putExtra(AutoRaft.GPSMESSENGER, gpsMessenger);
        Messenger btMessenger = new Messenger(mSerialService.getAutoPilotHandler());
        intent.putExtra(AutoRaft.SERIALMESSENGER, btMessenger);
        startService(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.auto_raft, menu); // FIXME Might be in other order
        mMenuItemConnect = menu.getItem(0);
        mMenuItemAutoPilot = menu.getItem(1);
        mMenuItemWaypointList = menu.getItem(2);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                }
                else
                if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
                    mSerialService.stop();
                    mSerialService.start();
                }
                return true;
            case R.id.auto_pilot:
                // If Auto pilot is on, navigate to dest TODO More notification that autopilot is on/off
                autoPilotOn = !autoPilotOn;
                if (autoPilotOn) {
                    mMenuItemAutoPilot.setTitle(R.string.start_auto_pilot);
                } else {
                    mMenuItemAutoPilot.setTitle(R.string.stop_auto_pilot);
                }
                sendAutoPilotOn(autoPilotOn);
                return true;
            case R.id.waypoint_list:
                Intent waypointListIntent = new Intent(this, WaypointListActivity.class);
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(AutoRaft.WAYPOINTLIST, getArrayList(wayPoints));
                startActivityForResult(waypointListIntent, REQUEST_WAYPOINT);
                return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public int getConnectionState() {
        return mSerialService.getState();
    }

    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            if (mMenuItemConnect != null) {
                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
                                mMenuItemConnect.setTitle(R.string.disconnect);
                            }
                            mTitle.setText( R.string.title_connected_to );
                            mTitle.append(" " + mConnectedDeviceName);
                            break;

                        case BluetoothSerialService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;

                        case BluetoothSerialService.STATE_LISTEN:
                        case BluetoothSerialService.STATE_NONE:
                            if (mMenuItemConnect != null) {
                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
                                mMenuItemConnect.setTitle(R.string.connect);
                            }
                            mTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
		/*                
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;              
                mEmulatorView.write(readBuf, msg.arg1);

                break;
		 */
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private final Handler mAutoPilotHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE:
                    if (msg.getData().getBoolean("AUTO_PILOT_STATE")) {
                        mMenuItemAutoPilot.setTitle(R.string.stop_auto_pilot);
                        autoPilotOn = true;
                    } else {
                        mMenuItemAutoPilot.setTitle(R.string.start_auto_pilot);
                        autoPilotOn = false;
                    }
                    break;

                case MESSAGE_LOCATION_CHANGED:

                    raftBearing = msg.getData().getFloat(AutoRaft.BEARING);
                    raftSpeed = msg.getData().getFloat(AutoRaft.SPEED);
                    raftPosLong = msg.getData().getDouble(AutoRaft.LONG);
                    raftPosLat = msg.getData().getDouble(AutoRaft.LAT);
                    raftPos = new LatLng(raftPosLat, raftPosLong);

                    currentDest = msg.getData().getInt(AutoRaft.CURRENT_DEST);
                    bearingToDest = msg.getData().getFloat(AutoRaft.BEARING_TO_DEST);
                    angleToDest = msg.getData().getFloat(AutoRaft.ANGLE_TO_DEST);

                    if (!mapLocked) {
                        if (!wayPoints.isEmpty()) {
                            for (Marker m : wayPoints)
                                m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                            wayPoints.get(currentDest).setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                        }

                        if (previousRaftPos == null) previousRaftPos = raftPos;
                        // Animate camera on position
                        if (focusOnPosition) animateNavMode();

                        headingSeekBarValue.setText("Bearing: " + bearingToDest + "Angle: " + angleToDest);
                        textview1.setText("RaftBearing: " + raftBearing);
                        textview2.setText("Current dest: " + currentDest);

                        // DRAW LINES
                        if (headingLine != null) headingLine.remove();
                        headingLine = googleMap.addPolyline(new PolylineOptions()
                                .add(new LatLng(raftPos.latitude, raftPos.longitude), AutoPilotService.newPosition(raftPos.latitude, raftPos.longitude, raftBearing, 0.1))
                                .width(4)
                                .color(Color.RED));

                        if (destinationLine != null) destinationLine.remove();
                        if (wayPoints.size() > 0) {
                            destinationLine = googleMap.addPolyline(new PolylineOptions()
                                    .add(new LatLng(raftPos.latitude, raftPos.longitude), wayPoints.get(currentDest).getPosition())
                                    .width(1)
                                    .color(Color.GREEN));
                        }

                        if (searchLines != null) searchLines.remove();
                        searchLines = googleMap.addPolyline(new PolylineOptions()
                                .add(AutoPilotService.newPosition(raftPosLat, raftPosLong, raftBearing - AutoPilotService.SEARCH_WIDTH, 0.2), raftPos, AutoPilotService.newPosition(raftPosLat, raftPosLong, raftBearing + AutoPilotService.SEARCH_WIDTH, 0.2))
                                .width(2)
                                .color(Color.YELLOW));

                        // Save previous raft pos and trail // TODO Save lines in array, and re draw each all(??) lines??
                        if (distanceBetween(previousRaftPos, raftPos) > 10) {
                            raftTrail.add(googleMap.addPolyline(new PolylineOptions().add(previousRaftPos, raftPos).color(Color.GRAY).width(3)));
                            previousRaftPos = raftPos;
                        }
                    }
                    break;
            }
        }
    };

    private void animateNavMode() {
        float bearing;
        LatLng target;
        if (wayPoints.size() < 1) {
            bearing = raftBearing;
            target = AutoPilotService.newPosition(raftPosLat, raftPosLong, raftBearing, OFFSET);
        } else {
            bearing = bearingToDest;
            target = AutoPilotService.newPosition(raftPosLat, raftPosLong, bearingToDest, OFFSET);
        }
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(target)
                .zoom(18)
                .bearing(bearing)
                .tilt(80)
                .build();
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable( false )
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mSerialService.connect(device);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {
                    finishDialogNoBluetooth();
                }
                break;

            case REQUEST_WAYPOINT:
                // Get waypointlist from waypoint list activity
                data.getIntExtra(EXTRA_ROUTE_NUMBER, -1); // TODO Catch -1 if noting is gotten from Extra.
                break;
        }
    }

    /**
     * function to load map. If map is not created it will create it for you
     * */
    private void initilizeMap() {
        if (googleMap == null) {
            googleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.google_map)).getMap();
            // check if map is created successfully or not
            if (googleMap == null) {
                Toast.makeText(getApplicationContext(),
                        "Sorry! unable to create maps", Toast.LENGTH_SHORT)
                        .show();
            } else {
                googleMap.setMyLocationEnabled(true); // TODO Change to custom raft icon
                googleMap.setBuildingsEnabled(false);
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(UMEA));
                googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                    @Override
                    public void onCameraChange(CameraPosition cameraPosition) {
                        if ( raftPos != null) {
                            Location raftLocation = new Location(LocationManager.PASSIVE_PROVIDER);
                            raftLocation.setLatitude(raftPos.latitude);
                            raftLocation.setLongitude(raftPos.longitude);

                            Location cameraLocation = new Location(LocationManager.PASSIVE_PROVIDER);
                            LatLng cameraLatLng = AutoPilotService.newPosition(cameraPosition.target.latitude, cameraPosition.target.longitude, raftBearing, -1*OFFSET);
                            cameraLocation.setLatitude(cameraLatLng.latitude);
                            cameraLocation.setLongitude(cameraLatLng.longitude);
                            focusOnPosition  = cameraLocation.distanceTo(raftLocation) < 50 && cameraPosition.tilt > 50;
                        }
                    }
                });

                googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        // Add marker
                        wayPoints.add(googleMap.addMarker(new MarkerOptions().position(latLng).draggable(true).title(""+wayPoints.size()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))));

                        // Send waypoints to service
                        sendWaypointList();

                        // Connect lines between all the waypoints
                        if ( wayPoints.size() > 1 )
                            wayPointLines.add(googleMap.addPolyline(new PolylineOptions().add(wayPoints.get(wayPoints.size()-1).getPosition(),wayPoints.get(wayPoints.size()-2).getPosition()).color(Color.GREEN)));
                    }
                });
                googleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                    @Override
                    public boolean onMyLocationButtonClick() {
                        animateNavMode();
                        return true;
                    }
                });
                googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                    int i;
                    @Override
                    public void onMarkerDragStart(Marker marker) {
                        mapLocked = true;
                        i = wayPoints.indexOf(marker);
                    }

                    @Override
                    public void onMarkerDrag(Marker marker) {
                        // Move Polylines connected to marker
                        if (i != wayPoints.size() - 1) { // If marker is not last waypoint
                            wayPointLines.get(i).remove();
                            wayPointLines.add(i, googleMap.addPolyline(new PolylineOptions().add(wayPoints.get(i).getPosition(), wayPoints.get(i + 1).getPosition()).color(Color.GREEN)));
                        } else if (i != 0) { // If marker is not first waypoint
                            wayPointLines.get(i - 1).remove();
                            wayPointLines.add(i - 1, googleMap.addPolyline(new PolylineOptions().add(wayPoints.get(i).getPosition(), wayPoints.get(i - 1).getPosition()).color(Color.GREEN)));
                        }
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                        // If marker is dropped closer than 10m remove marker
                        if (distanceBetween(marker.getPosition(), wayPoints.get(i-1).getPosition()) < 10) wayPoints.remove(i);
                        else if (distanceBetween(marker.getPosition(), wayPoints.get(i+1).getPosition()) < 10) wayPoints.remove(i);
                        sendWaypointList();
                        mapLocked = false;
                    }
                });
            }
        }
    }

    private void sendWaypointList() {
        Intent intent = new Intent(getBaseContext(), AutoPilotService.class);
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(AutoRaft.WAYPOINTLIST, getArrayList(wayPoints));
        intent.putExtra(AutoRaft.WAYPOINTBUNDLE, bundle);
        startService(intent);
    }

    private void sendAutoPilotOn(boolean autoPilotOn) {
        Intent intent = new Intent(getBaseContext(), AutoPilotService.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean(AutoRaft.AUTOPILOTON, autoPilotOn);
        intent.putExtra(AutoRaft.AUTOPILOTON, bundle);
        startService(intent);
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

    private ArrayList<LatLng> getArrayList(ArrayList<Marker> markerList) {
        ArrayList<LatLng> latLngs = new ArrayList<LatLng>();
        for ( Marker m : markerList ) {
            latLngs.add(new LatLng(m.getPosition().latitude, m.getPosition().longitude));
        }
        return latLngs;
    }
}
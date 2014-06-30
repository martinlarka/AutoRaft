package se.martinlarka.autoraft.app.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import se.martinlarka.autoraft.app.AutoPilotService;
import se.martinlarka.autoraft.app.AutoRaft;
import se.martinlarka.autoraft.app.R;

/**
 * Created by martin on 2014-06-29.
 */
public class NavigationFragment extends Fragment {

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


    private static TextView headingSeekBarValue;
    private static TextView textview1;
    private static TextView textview2;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AutoRaft autoRaft = (AutoRaft) getActivity();

        // Start auto pilot service for gps readings
        Intent intent = new Intent(getActivity(), AutoPilotService.class);
        Messenger gpsMessenger = new Messenger(mAutoPilotHandler);
        intent.putExtra(AutoRaft.GPSMESSENGER, gpsMessenger);
        Messenger btMessenger = new Messenger(autoRaft.getSerialService().getAutoPilotHandler());
        intent.putExtra(AutoRaft.SERIALMESSENGER, btMessenger);
        getActivity().startService(intent);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_navigation, container, false);

        headingSeekBarValue = (TextView) rootView.findViewById(R.id.seek_bar_value);
        textview1 = (TextView) rootView.findViewById(R.id.textview1);
        textview2 = (TextView) rootView.findViewById(R.id.textview2);

        try {
            // Loading map
            initilizeMap();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return rootView;
    }

    /**
     * function to load map. If map is not created it will create it for you
     * */
    private void initilizeMap() {
        if (googleMap == null) {
            googleMap = ((MapFragment) getActivity().getFragmentManager().findFragmentById(R.id.google_map)).getMap();
            // check if map is created successfully or not
            if (googleMap == null) {
                Toast.makeText(getActivity().getApplicationContext(),
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
                            LatLng cameraLatLng = AutoPilotService.newPosition(cameraPosition.target.latitude, cameraPosition.target.longitude, raftBearing, -1 * OFFSET);
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
                            wayPointLines.remove(i);
                            wayPointLines.add(i, googleMap.addPolyline(new PolylineOptions().add(wayPoints.get(i).getPosition(), wayPoints.get(i + 1).getPosition()).color(Color.GREEN)));
                        }
                        if (i != 0) { // If marker is not first waypoint
                            wayPointLines.get(i - 1).remove();
                            wayPointLines.remove(i - 1);
                            wayPointLines.add(i - 1, googleMap.addPolyline(new PolylineOptions().add(wayPoints.get(i).getPosition(), wayPoints.get(i - 1).getPosition()).color(Color.GREEN)));
                        }
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                        // If marker is dropped closer than 10m remove marker
                        if (i == 0 && wayPoints.size() > 1) { // If marker is first waypoint
                            if (distanceBetween(marker.getPosition(), wayPoints.get(i+1).getPosition()) < 20) {
                                wayPoints.get(i+1).remove();
                                wayPoints.remove(i+1);
                                wayPointLines.get(i).remove();
                                wayPointLines.remove(i);
                            }
                        }
                        else if (i == wayPoints.size() - 1 && wayPoints.size() > 1) { // If marker is last waypoint
                            if (distanceBetween(marker.getPosition(), wayPoints.get(i-1).getPosition()) < 20) {
                                wayPoints.get(i-1).remove();
                                wayPoints.remove(i-1);
                                wayPointLines.get(i-1).remove();
                                wayPointLines.remove(i-1);
                            }
                        }
                        else if (wayPoints.size() == 1) {
                            // If only one waypoint, just move marker
                        }
                        else if (distanceBetween(marker.getPosition(), wayPoints.get(i-1).getPosition()) < 20 || distanceBetween(marker.getPosition(), wayPoints.get(i+1).getPosition()) < 20) {
                            wayPoints.get(i).remove();
                            wayPoints.remove(i);
                            wayPointLines.get(i-1).remove();
                            wayPointLines.remove(i-1);
                        }
                        if (currentDest >= wayPoints.size()) {
                            currentDest = wayPoints.size()-1;
                        }
                        sendWaypointList();
                        mapLocked = false;
                    }
                });
            }
        }
    }

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

    private void sendWaypointList() {
        Intent intent = new Intent(getActivity().getBaseContext(), AutoPilotService.class);
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(AutoRaft.WAYPOINTLIST, getArrayList(wayPoints));
        bundle.putInt(AutoRaft.CURRENT_DEST, currentDest);
        intent.putExtra(AutoRaft.WAYPOINTBUNDLE, bundle);
        getActivity().startService(intent);
    }

    private void sendAutoPilotOn(boolean autoPilotOn) {
        Intent intent = new Intent(getActivity().getBaseContext(), AutoPilotService.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean(AutoRaft.AUTOPILOTON, autoPilotOn);
        intent.putExtra(AutoRaft.AUTOPILOTON, bundle);
        getActivity().startService(intent);
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

        return new Float(distance * meterConversion);
    }

    private ArrayList<LatLng> getArrayList(ArrayList<Marker> markerList) {
        ArrayList<LatLng> latLngs = new ArrayList<LatLng>();
        for ( Marker m : markerList ) {
            latLngs.add(new LatLng(m.getPosition().latitude, m.getPosition().longitude));
        }
        return latLngs;
    }

    private final Handler mAutoPilotHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AutoRaft.MESSAGE_LOCATION_CHANGED:

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

    public Handler getmAutoPilotHandler() {
        return mAutoPilotHandler;
    }
}

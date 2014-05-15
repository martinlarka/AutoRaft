package se.martinlarka.autoraft.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


public class WaypointListActivity extends Activity {

    private static final String WAYPOINTLAT = "waypoint_lat";
    private static final String WAYPOINTLNG = "waypoint_lng";
    private static String ROUTES_ARRAY ="routes_array";
    private static String FILENAME = "autoraftroutes3";
    private static final String ROUTE_NAME = "route_name";
    private ArrayList<LatLng> currentRoute;
    private ArrayList<ArrayList<LatLng>> routesLatLng;
    private ArrayList<String> routesNames;
    private ArrayAdapter<String> routesListAdapter; //TODO Style routes like cards new adapter needed.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint_list);

        Bundle bundle = getIntent().getBundleExtra(AutoRaft.WAYPOINTLIST);
        currentRoute = bundle.getParcelableArrayList(AutoRaft.WAYPOINTLIST);

        TextView currentRouteTextView = (TextView) findViewById(R.id.current_route);
        currentRouteTextView.setOnEditorActionListener(currentRouteSaveListener);

        routesListAdapter = new ArrayAdapter<String>(this, R.layout.route_name);
        ListView routeList = (ListView) findViewById(R.id.routes_list);
        routeList.setAdapter(routesListAdapter);
        routeList.setOnItemClickListener(routeItemClickListener);

        // Get saved routes
        JSONArray jsonRoutes = getSavedRoutes();
        routesNames = getSavedRoutesName(jsonRoutes);
        routesLatLng = getSavedRouteLatLng(jsonRoutes);

        // Fill route list with routes
        for (String route : routesNames) {
            routesListAdapter.add(route);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    // Read JSON from internal file
    private JSONArray getSavedRoutes() {
        FileInputStream fis = null;
        StringBuffer fileContent = new StringBuffer("");
        JSONArray routes = null;
        try {
            fis = openFileInput(FILENAME);
            byte[] buffer = new byte[1024];
            int n;

            while ((n = fis.read(buffer)) != -1) {
                fileContent.append(new String(buffer, 0, n));
            }
            fis.close();
            Log.d("test", fileContent.toString());
            routes = new JSONArray(fileContent.toString());

        } catch (FileNotFoundException e) {
            Log.d("WAYPOINTLIST", "File not found");
        } catch (IOException e) {
            Log.d("WAYPOINTLIST", "IO Exception");
        } catch (JSONException e) {
            Log.d("WAYPOINTLIST", "Could not make JSON object");
        } finally {
            try{if(fis != null) fis.close();}catch(Exception squish){}
        }
        return routes;
    }

    private void saveRoute(ArrayList<LatLng> waypoints, String routeName) {
        // Get current route list
        JSONArray savedRoutes = getSavedRoutes();
        if (savedRoutes == null) {
            savedRoutes = new JSONArray();
        }
        try {
            JSONArray jWaypoints = new JSONArray();
            for (LatLng pos : waypoints) {
                JSONObject waypoint = new JSONObject();
                waypoint.put(WAYPOINTLAT, pos.latitude);
                waypoint.put(WAYPOINTLNG, pos.longitude);
                jWaypoints.put(waypoint);
            }
            JSONObject route = new JSONObject();
            route.put(ROUTE_NAME,routeName);
            route.put(ROUTES_ARRAY, jWaypoints);
            savedRoutes.put(route);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        FileOutputStream fos = null;

        try {
            fos = openFileOutput(FILENAME, Context.MODE_PRIVATE);
            fos.write(savedRoutes.toString().getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{if(fos != null) fos.close();}catch(Exception squish){}
        }
    }

    // Get route names from JSON object
    private ArrayList<String> getSavedRoutesName(JSONArray routes) {
        ArrayList<String> rn = new ArrayList<String>();
        if (routes == null) return  rn;
        // Get routes JSON array
        try {
            for (int i=0; i < routes.length(); i++) {
                JSONObject route = routes.getJSONObject(i);
                rn.add(route.getString(ROUTE_NAME));
            }
        } catch (JSONException e) {
            Log.d("WAYPOINTLIST", "Could not get route names");
        }

        return rn;
    }

    // Get routes in LatLng
    private ArrayList<ArrayList<LatLng>> getSavedRouteLatLng(JSONArray routes) {
        ArrayList<ArrayList<LatLng>> routesList = new ArrayList<ArrayList<LatLng>>();
        if (routes == null) return routesList;
        // Get routes JSON array
        try {
            for (int i=0; i < routes.length(); i++) {
                JSONArray jRouteArray = routes.getJSONArray(i);
                routesList.add(i, new ArrayList<LatLng>());
                for (int j=0; j < jRouteArray.length(); j++) {
                    JSONObject jWaypoint = jRouteArray.getJSONObject(j);
                    routesList.get(i).add(new LatLng(jWaypoint.getDouble(WAYPOINTLAT), jWaypoint.getDouble(WAYPOINTLNG)));
                }
            }
        } catch (JSONException e) {
            Log.d("WAYPOINTLIST", "Could not get route waypoints");
        }

        return routesList;
    }


    private OnItemClickListener routeItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Intent intent = new Intent();
            intent.putExtra(AutoRaft.EXTRA_ROUTE_NUMBER, i);

            // Set result and finish this Activity
            setResult(AutoRaft.REQUEST_WAYPOINT, intent);

            finish();
        }
    };

    private TextView.OnEditorActionListener currentRouteSaveListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
            if (i == EditorInfo.IME_ACTION_DONE || keyEvent != null) {
                saveRoute(currentRoute, textView.getText().toString());
                finish();
                return true;
            }
            return false;
        }
    };

}

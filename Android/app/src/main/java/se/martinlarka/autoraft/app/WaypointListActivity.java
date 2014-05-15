package se.martinlarka.autoraft.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;


public class WaypointListActivity extends Activity {
    private ArrayList<LatLng> currentRoute;
    private ArrayList<String> savedRoutes = new ArrayList<String>();
    private ArrayAdapter<String> routesListAdapter; //TODO Style routes like cards new adapter needed.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentRoute = savedInstanceState.getParcelableArrayList(AutoRaft.WAYPOINTLIST);
        setContentView(R.layout.activity_waypoint_list);

        TextView currentRouteTextView = (TextView) findViewById(R.id.current_route);
        currentRouteTextView.setOnEditorActionListener(currentRouteSaveListener);

        routesListAdapter = new ArrayAdapter<String>(this, R.layout.route_name);
        ListView routeList = (ListView) findViewById(R.id.routes_list);
        routeList.setAdapter(routesListAdapter);
        routeList.setOnItemClickListener(routeItemClickListener);

        // Get saved routes FIXME

        // Fill route list with routes
        for (String route : savedRoutes) {
            routesListAdapter.add(route);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
            if ( keyEvent != null ) {
                // Save route

                return true;
            }
            return false;
        }
    };

}

package se.martinlarka.autoraft.app.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import se.martinlarka.autoraft.app.R;

/**
 * Created by martin on 2014-06-29.
 */
public class WaypointsFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_waypoints, container, false);

        return rootView;
    }

}

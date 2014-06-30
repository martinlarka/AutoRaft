package se.martinlarka.autoraft.app.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import se.martinlarka.autoraft.app.fragment.ManualNavigationFragment;
import se.martinlarka.autoraft.app.fragment.NavigationFragment;
import se.martinlarka.autoraft.app.fragment.WaypointsFragment;

/**
 * Created by martin on 2014-06-29.
 */
public class TabsPagerAdapter extends FragmentPagerAdapter {
    public TabsPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                // Top Rated fragment activity
                return new WaypointsFragment();
            case 1:
                // Games fragment activity
                return new NavigationFragment();
            case 2:
                // Movies fragment activity
                return new ManualNavigationFragment();
        }

        return null;
    }

    @Override
    public int getCount() {
        return 3;
    }
}

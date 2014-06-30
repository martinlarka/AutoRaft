package se.martinlarka.autoraft.app.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import se.martinlarka.autoraft.app.AutoRaft;
import se.martinlarka.autoraft.app.BluetoothSerialService;
import se.martinlarka.autoraft.app.R;

/**
 * Created by martin on 2014-06-29.
 */
public class ManualNavigationFragment extends Fragment {

    private SeekBar manualSeekBar;
    private BluetoothSerialService mSerialService;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        AutoRaft autoRaft = (AutoRaft) getActivity();
        mSerialService = autoRaft.getSerialService();

        View rootView = inflater.inflate(R.layout.fragment_manual_navigation, container, false);

        manualSeekBar = (SeekBar) rootView.findViewById(R.id.manual_seek_bar);
        manualSeekBar.setMax(120);
        manualSeekBar.setProgress(60);

        manualSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float value = seekBar.getProgress() - 60;
                Log.d("TEST", ""+value);
                if (value < -60) mSerialService.write(0);
                else if (value > 60) mSerialService.write(255);
                else mSerialService.write(Math.round(2*value + 255/2)); // Maps angles between -60 and 60 to a one byte value
            }
        });

        return rootView;
    }

}

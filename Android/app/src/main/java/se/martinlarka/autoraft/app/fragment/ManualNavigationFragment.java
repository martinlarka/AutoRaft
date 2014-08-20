package se.martinlarka.autoraft.app.fragment;

import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.Timer;
import java.util.TimerTask;

import se.martinlarka.autoraft.app.AutoRaft;
import se.martinlarka.autoraft.app.BluetoothSerialService;
import se.martinlarka.autoraft.app.CenteringTimerTask;
import se.martinlarka.autoraft.app.R;

/**
 * Created by martin on 2014-06-29.
 */
public class ManualNavigationFragment extends Fragment {

    private SeekBar manualSeekBar;
    private TextView currentValue;
    private BluetoothSerialService mSerialService;
    private int lastOutput = 0;
    private LinearLayout background;
    private Timer centerOutputTimer;
    private CenteringTimerTask centerOutputTimerTask;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        AutoRaft autoRaft = (AutoRaft) getActivity();
        mSerialService = autoRaft.getSerialService();

        View rootView = inflater.inflate(R.layout.fragment_manual_navigation, container, false);
        background = (LinearLayout) rootView.findViewById(R.id.background);
        currentValue = (TextView) rootView.findViewById(R.id.current_value_textview);
        currentValue.setText("0");

        centerOutputTimer = new Timer();

        manualSeekBar = (SeekBar) rootView.findViewById(R.id.manual_seek_bar);
        manualSeekBar.setMax(120);
        manualSeekBar.setProgress(60);

        manualSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                int value = i - 60;
                currentValue.setText("" + value);
                Log.d("TEST", "Last output " + lastOutput);
                if (value > lastOutput + 10 || value < lastOutput - 10 || value == 0) {
                    sendOutput(value);
                    lastOutput = value;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (centerOutputTimerTask != null) centerOutputTimerTask.stopTimer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = seekBar.getProgress() - 60;
                sendOutput(value);
                centerOutputTimerTask = new CenteringTimerTask(manualSeekBar);
                centerOutputTimer.schedule(centerOutputTimerTask, 0, 1000/6);
            }
        });

        return rootView;
    }

    private void sendOutput(int output) {
        Log.d("TEST", "" + output);
        lastOutput = output;
        if (output < -60) mSerialService.write(0);
        else if (output > 60) mSerialService.write(255);
        else mSerialService.write(Math.round(2*output + 255/2)); // Maps angles between -60 and 60 to a one byte value
        flashScreen();
    }

    private void flashScreen() {
        final int DELAY = 25;

        AnimationDrawable a = new AnimationDrawable();

        for (int i = -100; i<100; i= i + 10) {
            ColorDrawable f = new ColorDrawable(Color.GRAY);
            f.setAlpha(Math.abs(i));
            a.addFrame(f, DELAY);
        }
        ColorDrawable f = new ColorDrawable(Color.WHITE);
        a.addFrame(f, DELAY);
        a.setOneShot(true);

        background.setBackground(a);
        a.start();
    }

}

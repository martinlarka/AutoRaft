package se.martinlarka.autoraft.app;

import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.TimerTask;

/**
 * Created by martin on 2014-08-20.
 */
public class CenteringTimerTask extends TimerTask {
    private SeekBar manualSeekBar;

    public CenteringTimerTask(SeekBar seekBar) {
        this.manualSeekBar = seekBar;
    }

    @Override
    public void run() {
        int seekBarValue = manualSeekBar.getProgress();
        if (seekBarValue > 60)
            manualSeekBar.setProgress(seekBarValue - 1);
        else if (seekBarValue < 60)
            manualSeekBar.setProgress(seekBarValue + 1);
        else
            this.cancel();

    }

    public void stopTimer() {
        this.cancel();
    }
}

package se.martinlarka.autoraft.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class AutoPilotService extends Service {

    private Messenger mMessenger;
    private boolean isNavigating = false;
    private Timer navTimer = new Timer();

    public AutoPilotService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mMessenger = (Messenger) extras.get("MESSENGER");
        } else {
            mMessenger = null;
        }
        Toast.makeText(this, R.string.auto_pilot_started, Toast.LENGTH_LONG).show();
        NavigationTask navTask = new NavigationTask();
        navTimer.schedule(navTask, 1000, 1000);

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        navTimer.cancel();
        Toast.makeText(this, R.string.auto_pilot_stopped, Toast.LENGTH_LONG).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class NavigationTask extends TimerTask {
        public void run() {
            Message msg = Message.obtain(null, AutoRaft.MESSAGE_HEADING);
            Bundle bundle = new Bundle();
            bundle.putInt("AUTO_PILOT_HEADING", 50);
            msg.setData(bundle);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                Log.w(getClass().getName(), "Exception sending message");
            }
        }
    }
}

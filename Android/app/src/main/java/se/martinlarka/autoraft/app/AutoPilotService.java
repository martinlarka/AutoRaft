package se.martinlarka.autoraft.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

public class AutoPilotService extends Service {

    private Messenger mMessenger;
    private boolean isNavigating = false;

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
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, R.string.auto_pilot_stopped, Toast.LENGTH_LONG).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

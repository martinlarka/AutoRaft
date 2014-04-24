package se.martinlarka.autoraft.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class AutoRaft extends Activity {
    
 // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;	
    
    private static TextView mTitle;
    private static TextView headingSeekBarValue;
    private static SeekBar headingSeekBar;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    private BluetoothAdapter mBluetoothAdapter = null;

    private static BluetoothSerialService mSerialService = null;
    
    private MenuItem mMenuItemConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_auto_raft);
	
	// Set up the custom title
	mTitle = (TextView) findViewById(R.id.device);
	mTitle.setText(R.string.title_not_connected);
	
	headingSeekBar = (SeekBar) findViewById(R.id.heading_seek_bar);
	headingSeekBarValue = (TextView) findViewById(R.id.seek_bar_value);
	
	headingSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
	    
	    @Override
	    public void onStopTrackingTouch(SeekBar seekBar) {}
	    
	    @Override
	    public void onStartTrackingTouch(SeekBar seekBar) {}
	    
	    @Override
	    public void onProgressChanged(SeekBar seekBar, int progress,
		    boolean fromUser) {
		String tempStr = Integer.toString(progress) + "\n";
		headingSeekBarValue.setText(tempStr);
		mSerialService.write(tempStr.getBytes());
	    }
	});
	
	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	if (mBluetoothAdapter == null) {
	    //finishDialogNoBluetooth();
	    return;
	}
	
	mSerialService = new BluetoothSerialService(this, mHandlerBT, headingSeekBar);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	// Inflate the menu; this adds items to the action bar if it is present.
	getMenuInflater().inflate(R.menu.auto_raft, menu);
	mMenuItemConnect = menu.getItem(0);
	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
        	if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
        		// Launch the DeviceListActivity to see devices and do scan
        		Intent serverIntent = new Intent(this, DeviceListActivity.class);
        		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        	}
        	else
            	if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
            		mSerialService.stop();
		    	mSerialService.start();
            	}
            return true;
        case R.id.action_settings:
            return true;
        }
        return false;
    }
    
    public int getConnectionState() {
	return mSerialService.getState();
    }
    
    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {

	@Override
	public void handleMessage(Message msg) {        	
	    switch (msg.what) {
	    case MESSAGE_STATE_CHANGE:
		switch (msg.arg1) {
		case BluetoothSerialService.STATE_CONNECTED:
		    if (mMenuItemConnect != null) {
			mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
			mMenuItemConnect.setTitle(R.string.disconnect);
		    }
		    mTitle.setText( R.string.title_connected_to );
		    mTitle.append(" " + mConnectedDeviceName);
		    break;

		case BluetoothSerialService.STATE_CONNECTING:
		    mTitle.setText(R.string.title_connecting);
		    break;

		case BluetoothSerialService.STATE_LISTEN:
		case BluetoothSerialService.STATE_NONE:
		    if (mMenuItemConnect != null) {
			mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
			mMenuItemConnect.setTitle(R.string.connect);
		    }
		    mTitle.setText(R.string.title_not_connected);
		    break;
		}
		break;
	    case MESSAGE_WRITE:
		break;
		/*                
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;              
                mEmulatorView.write(readBuf, msg.arg1);

                break;
		 */                
	    case MESSAGE_DEVICE_NAME:
		mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
		Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " "
			+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
		break;
	    case MESSAGE_TOAST:
		Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
		break;
	    }
	}
    };  
    
    public void finishDialogNoBluetooth() {
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	builder.setMessage(R.string.alert_dialog_no_bt)
	.setIcon(android.R.drawable.ic_dialog_info)
	.setTitle(R.string.app_name)
	.setCancelable( false )
	.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog, int id) {
		finish();            	
	    }
	});
	AlertDialog alert = builder.create();
	alert.show(); 
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
 	switch (requestCode) {

 	case REQUEST_CONNECT_DEVICE:

 	    // When DeviceListActivity returns with a device to connect
 	    if (resultCode == Activity.RESULT_OK) {
 		// Get the device MAC address
 		String address = data.getExtras()
 			.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
 		// Get the BLuetoothDevice object
 		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
 		// Attempt to connect to the device
 		mSerialService.connect(device);                
 	    }
 	    break;

 	case REQUEST_ENABLE_BT:
 	    // When the request to enable Bluetooth returns
 	    if (resultCode != Activity.RESULT_OK) {
 		finishDialogNoBluetooth();                
 	    }
 	}
     }

    
}

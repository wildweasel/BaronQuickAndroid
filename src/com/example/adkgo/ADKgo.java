package com.example.adkgo;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

public class ADKgo extends Activity {
	
	private static final String TAG = ADKgo.class.getSimpleName();

	private PendingIntent mPermissionIntent;
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private boolean mPermissionRequestPending;

	private UsbManager mUsbManager;
	private UsbAccessory mAccessory;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;
	 
    private static final byte COMMAND_START = -0x2; 

    private TextView outputTouchPoint;
    private TextView outputMotorCommand;
    private TextView outputPolar;
    private Controller controller; 
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		setContentView(R.layout.activity_adkgo);
		
		outputTouchPoint = (TextView) findViewById(R.id.output_touch_coords);
		outputMotorCommand = (TextView) findViewById(R.id.output_motor_commands);
		outputPolar = (TextView) findViewById(R.id.output_polar);

		controller = (Controller) findViewById(R.id.controller);  
		controller.setOnTouchListener(new OnTouchListener(){

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				
				if(event.getAction() != MotionEvent.ACTION_UP){
					controller.registerTouch(event.getX(), event.getY());
				}
				else{
					controller.goHome();
				}
				outputTouchPoint.setText("Touch - X: "+controller.touchPoint.x+"; Y: "+controller.touchPoint.y);
				
				PointF motorPolar = controller.calcPolar();
				
				outputPolar.setText("Motor R: "+motorPolar.x+"; Motor Theta: "+motorPolar.y);
				
				Point motorCommand = getMotorSpeeds(motorPolar);
								
				outputMotorCommand.setText("Right Motor "+motorCommand.x+"%; Left Motor "+motorCommand.y+"%.");
								
				sendRCMotorCommand(motorCommand);

				return true;
			}});
    }

	/**
	 * Called when the activity is resumed from its paused state and immediately
	 * after onCreate().
	 */
	@Override
	public void onResume() {
		super.onResume();
//		controller.setup();
		if (mInputStream != null && mOutputStream != null) {
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}

	/** Called when the activity is paused by the system. */
	@Override
	public void onPause() {
		super.onPause();
		closeAccessory();
	}

	/**
	 * Called when the activity is no longer needed prior to being removed from
	 * the activity stack.
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mUsbReceiver);
	}
    
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

    private void openAccessory(UsbAccessory accessory) { 
        mFileDescriptor = mUsbManager.openAccessory(accessory); 
        if (mFileDescriptor != null) { 
            mAccessory = accessory; 
            FileDescriptor fd = mFileDescriptor.getFileDescriptor(); 
            mInputStream = new FileInputStream(fd); 
            mOutputStream = new FileOutputStream(fd); 
            Log.d(TAG, "accessory opened"); 
        } else { 
            Log.d(TAG, "accessory open fail"); 
        } 
    } 

	private void closeAccessory() {
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}

    public void sendRCMotorCommand(Point speeds) { 
        byte[] buffer = new byte[3]; 
        buffer[0] = COMMAND_START; 
        buffer[1] = (byte) speeds.x; 
		buffer[2] = (byte) speeds.y;
        
        Log.e("Output Sent Command", "Right motor "+buffer[1]+"%, Left motor "+buffer[2]+"%.");

        if (mOutputStream != null) { 
            try { 
                mOutputStream.write(buffer); 
            } catch (IOException e) { 
                Log.e(TAG, "write failed", e); 
            } 
        } 
    } 
    
    // Return motor speeds (RIGHT, LEFT)
	public Point getMotorSpeeds(PointF polarScale){
		float rScale = polarScale.x, thetaScale = polarScale.y;
		// The motors can't operate past 100%
		// Scale as to preserve angular velocity		
		// Too much left motor
		if(rScale > 2 * thetaScale)
			rScale = 2 * thetaScale;		
		// Too much right motor
		else if(rScale > -2 * thetaScale + 2)
			rScale = -2 * thetaScale + 2; 			
				
		// sit still at home 
		if(rScale == 0 && thetaScale == 0){
			return new Point(0,0);
		}
		
		// v_r = v_max * (rScale + 2*thetaScale - 1)
		// v_l = v_max * (rScale - 2*thetaScale + 1)
		return new Point((int) (100 * (rScale + 2*thetaScale - 1)), (int) (100 * (rScale - 2*thetaScale + 1)));
		
	}
}

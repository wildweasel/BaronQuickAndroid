package com.example.adkgo;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import com.android.future.usb.UsbManager;
import com.android.future.usb.UsbAccessory;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

public class ArduinoControl{
	
	// Easy access to the USB Permission action constant
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private static final String TAG = ADKgo.class.getSimpleName();
	
	Activity callingActivity;
	
	// Allows you to enumerate and communicate with connected USB accessories.
	UsbManager mUsbManager;
	
	// We don't want to trample on pending permission requests
	private boolean mPermissionRequestPending;
	
	private PendingIntent mPermissionIntent;

	
	// Represents a USB accessory and contains methods to access its identifying information.
	private UsbAccessory mAccessory;
	
	// The UsbManager provides a file descriptor that  can set up input and output streams
	// to read and write data to descriptor. The streams represent the accessory's input and
	// output bulk endpoints. 
	private ParcelFileDescriptor mFileDescriptor;	
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;

	private USBComms usbComms;
	
	Handler inputHandler;
	
	public ArduinoControl (Activity activity, Handler inputHandler){
		
		this.callingActivity = activity;
		this.inputHandler = inputHandler;
		
		// Get an instance of the USB Manager class to access the state of USB and communicate 
		// with USB devices.  This runs the USB show for the ADK
		mUsbManager = UsbManager.getInstance(callingActivity);
		
		// Keep on the lookout for a USB permission intent
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		// ...and the notification when the USB device is removed
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		// Register a BroadcastReceiver to catch these USB intents. 
		callingActivity.registerReceiver(mUsbReceiver, filter);

	}
	
	// What do we need to do when the intent filter catches a USB accessory intent?
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			// The OS has asked for permission to use the USB accessory - this indicates that one is connected
			// Right now, assume this also could be from a stored permission grant
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					// Find the USB accessory we just got permission for
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					// Do we have permission to open?
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						// then open
						openAccessory(accessory);
					} 
					else {
						Log.d(TAG, "permission denied for accessory " + accessory);
					}
					// Once we're here, we know the user has responded to the permission request
					//   - it's not pending anymore
					mPermissionRequestPending = false;
				}
			} 
			// The USB accesory has been removed
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					// close down the communication with the accessory
					closeAccessory();
				}
			}
		}
	};
	
	public void resume(){

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory, mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}
	
	public void pause(){
		closeAccessory();
	}

	public void destroy(){
		callingActivity.unregisterReceiver(mUsbReceiver);
	}
	
    private void openAccessory(UsbAccessory accessory) { 
        mFileDescriptor = mUsbManager.openAccessory(accessory); 
        if (mFileDescriptor != null) { 
            mAccessory = accessory; 
            FileDescriptor fd = mFileDescriptor.getFileDescriptor(); 
            mInputStream = new FileInputStream(fd); 
            mOutputStream = new FileOutputStream(fd); 
            
            usbComms = new USBComms(mInputStream, mOutputStream, inputHandler);
            Thread usbThread = new Thread(usbComms, "USB Communication Thread");
            usbThread.start();
            
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
	
	public void sendMessage(byte[] buffer){
		if(usbComms != null)
			usbComms.sendMessage(buffer);
	}
	
	private class USBComms implements Runnable{
		
		FileInputStream inputStream;
		FileOutputStream outputStream;
		Handler inputHandler;
		
		public USBComms(FileInputStream inputStream, FileOutputStream outputStream, Handler inputHandler){
			this.inputStream = inputStream;
			this.outputStream = outputStream;
			this.inputHandler = inputHandler;
		}
		
	
		// Send a message to the ADK device.  Return true on successful send, false otherwise
		public boolean sendMessage(byte[] buffer){
	        if (outputStream != null) { 
	            try { 
	                outputStream.write(buffer); 
	            } 
	            catch (IOException e) { 
	                Log.e(TAG, "write failed", e); 
	                return false;
	            } 
	        }
	        else
	        	return false;
	        return true;
		}

		@Override
		public void run() {
			int ret = 0;
			byte buffer[] = new byte[6];

			while(ret >= 0){

				// This blocks until it can read
				try {
					inputStream.read(buffer, 0, 6);
					Bundle bundle = new Bundle();
					bundle.putByteArray("data", buffer);	
					Message message = inputHandler.obtainMessage();
					message.setData(bundle);
					inputHandler.sendMessage(message);
					
				} catch (IOException e) {
	                Log.e(TAG, "read failed", e); 
					e.printStackTrace();
				}
				
			}
		}
	}
}

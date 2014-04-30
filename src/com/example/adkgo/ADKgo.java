package com.example.adkgo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.adkgo.SensorDisplayView.SensorType;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.Toast;

public class ADKgo extends Activity {
	
	// Don't want a leaky handler
	private static class SensorHandler extends Handler{
		private final WeakReference<ADKgo> mADKgo;
		
		public SensorHandler(ADKgo adkgo){
			mADKgo = new WeakReference<ADKgo>(adkgo);
		}
		
		@Override
		public void handleMessage(Message msg){
			ADKgo adkgo = mADKgo.get();
			if(adkgo != null){
				byte message[] = msg.getData().getByteArray("data");

				// parse message
				if(message[0] == OUTPUT_IR){
					
					int leftValue = message[1] & 0x000000FF;
					int leftCenterValue = message[2] & 0x000000FF;
					int centerValue = message[3] & 0x000000FF;
					int rightCenterValue = message[4] & 0x000000FF;
					int rightValue = message[5] & 0x000000FF;
					
					Log.w("ADKgo sensorHandler", leftValue+", "+leftCenterValue+", "+centerValue+", "+rightCenterValue+", "+rightValue);
					
					adkgo.sensorDisplayView.setValue(SensorType.LEFT_IR, leftValue);
					adkgo.sensorDisplayView.setValue(SensorType.LEFT_CENTER_IR, leftCenterValue);
					adkgo.sensorDisplayView.setValue(SensorType.CENTER_IR, centerValue);
					adkgo.sensorDisplayView.setValue(SensorType.RIGHT_CENTER_IR, rightCenterValue);
					adkgo.sensorDisplayView.setValue(SensorType.RIGHT_IR, rightValue);

					adkgo.sensorDisplayView.invalidate();
					
				}
				if(message[0] == OUTPUT_WE){
					//adkgo.outputMotorCommand.setText(message[1]+", "+message[2]);
					adkgo.sensorDisplayView.setLeftEncoderState((message[1] & 0x00000002) == 0, (message[2] & 0x00000002) != 0);
					adkgo.sensorDisplayView.setRightEncoderState((message[1] & 0x00000001) == 0, (message[2] & 0x00000001) != 0);
					
					
				}
			}
		}
	}
	
	private static final String TAG = ADKgo.class.getSimpleName();
	
	// Message types
	private static final int OUTPUT_IR = -3;
	private static final int OUTPUT_WE = -4;
	
	private ArduinoControl arduinoControl;
	 
    private static final byte COMMAND_START = -0x2; 

    private TextView outputMotorCommand;
    private Controller controller; 
    private SensorDisplayView sensorDisplayView;
        
    Handler sensorDataHandler;
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_adkgo);
		
		outputMotorCommand = (TextView) findViewById(R.id.output_motor_commands);
		
		sensorDisplayView = (SensorDisplayView) findViewById(R.id.sensor_display_view);
		sensorDisplayView.setMaxMin(255, 0);
		sensorDisplayView.setValue(SensorType.LEFT_IR, 0);
		sensorDisplayView.setValue(SensorType.LEFT_CENTER_IR, 30);
		sensorDisplayView.setValue(SensorType.CENTER_IR, 63);
		sensorDisplayView.setValue(SensorType.RIGHT_CENTER_IR, 96);
		sensorDisplayView.setValue(SensorType.RIGHT_IR, 127);

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
				Point motorCommand = getMotorSpeeds(controller.calcPolar());								
				outputMotorCommand.setText("Right Motor "+motorCommand.x+"%;\n Left Motor "+motorCommand.y+"%.");								
				sendRCMotorCommand(motorCommand);
				return true;
			}});
		
		sensorDataHandler = new SensorHandler(this);
		arduinoControl = new ArduinoControl(this, sensorDataHandler);	
		
		
    }

	@Override
	public void onResume() {
		super.onResume();
		
		arduinoControl.resume();
	}

	@Override
	public void onPause() {
		super.onPause();
		Toast.makeText(this, "onPause()", Toast.LENGTH_SHORT).show();
		arduinoControl.pause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Toast.makeText(this, "onDestroy()", Toast.LENGTH_SHORT).show();
		arduinoControl.destroy();
	}

    public void sendRCMotorCommand(Point speeds) { 
        byte[] buffer = new byte[3]; 
        buffer[0] = COMMAND_START; 
        buffer[1] = (byte) speeds.x; 
		buffer[2] = (byte) speeds.y;
        
        Log.e("Output Sent Command", "Right motor "+buffer[1]+"%, Left motor "+buffer[2]+"%.");

        arduinoControl.sendMessage(buffer); 
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

		// Too much right motor in reverse
		if(rScale < -2 * thetaScale)
			rScale = -2 * thetaScale;
		// Too much left motor in reverse
		else if(rScale < 2 * thetaScale - 2)
			rScale = 2 * thetaScale - 2;
			
		// sit still at home 
		if(rScale == 0 && thetaScale == 0){
			return new Point(0,0);
		}
		
		// v_r = v_max * (rScale + 2*thetaScale - 1)
		// v_l = v_max * (rScale - 2*thetaScale + 1)
		return new Point((int) (100 * (rScale + 2*thetaScale - 1)), (int) (100 * (rScale - 2*thetaScale + 1)));
		
	}
}

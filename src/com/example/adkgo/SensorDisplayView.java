package com.example.adkgo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class SensorDisplayView extends View {
	
	public enum SensorType{
		LEFT_IR(0),
		LEFT_CENTER_IR(1),
		CENTER_IR(4),
		RIGHT_CENTER_IR(8),
		RIGHT_IR(13);
		
		private final int pin;
		
		private SensorType(int pin){
			this.pin = pin;
		}
		
		public int getPin(){
			return pin;
		}
		
		private static final Map<Integer, SensorType> reverseLookupMap = new HashMap<Integer, SensorType>();
		static{
			for(SensorType s : SensorType.values())
			reverseLookupMap.put(s.pin, s);
		}
		
		public static SensorType getType(int pin){
			return reverseLookupMap.get(pin);
		}
	}
	
	// struct to bind sensor value to sensor color
	private class Sensor{
		int value;
		Paint paint = new Paint();
		void set(int value){
			this.value = value;
			this.paint.setColor(convertValueToColor(value));
		}
	}
	
	private EnumMap<SensorType, Sensor> sensorValues = new EnumMap<SensorType, Sensor>(SensorType.class);
	
	int maxValue;
	int minValue;
	
	Paint rightEncoderColor = new Paint();
	Paint leftEncoderColor = new Paint();

	public SensorDisplayView(Context context, AttributeSet attrs) {
		super(context, attrs);
		for(SensorType st : SensorType.values())
			sensorValues.put(st, new Sensor());
	}
	
	public void setMaxMin(int max, int min) {
		this.maxValue = max;
		this.minValue = min;
	}

	
	int convertValueToColor(int value){
		// Big numbers mean close, little numbers mean far
		
		//Log.e("adk convert", "Value: "+value);
		
		// Saturate
		if(value >= maxValue)
			return 0xFFFF0000; //red
		if (value <= minValue)
			return 0xFF00FF00;  //green
					
		// Close:  Yellow to Red
		if(value > 0x7F){
			int green = ~(value << 1);
			return 0xFFFF0000 | (green << 8);
		}
		// Far Away:  Green to Yellow
		else{
			int red = value << 1;
			return (red << 16) | 0xFF00FF00;
		}
	}
	
	public void setValue(SensorType sensor, int value){
		//Log.w("SDV set", "Set "+sensor.name()+ " to "+value);
		sensorValues.get(sensor).set(value);
	}
	
	public void setLeftEncoderState(boolean blocked, boolean forward){
		// Shade denotes state - dark is blocked, light is open
		int color = blocked ? 0xFF000000 : 0x5F000000;
		// Color denotes direction
		if(!forward)
			color += 0x000000FF;
		this.leftEncoderColor.setColor(color);
	}

	public void setRightEncoderState(boolean blocked, boolean forward){
		// Shade denotes state - dark is blocked, light is open
		int color = blocked ? 0xFF000000 : 0x5F000000;
		// Color denotes direction
		if(!forward)
			color += 0x000000FF;
		this.rightEncoderColor.setColor(color);
	}
	
	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);
		
		int width = this.getWidth();
		int height = this.getHeight();
		
		int radius =20;
				
		// center
		canvas.drawCircle(width/2, height/8, radius, sensorValues.get(SensorType.CENTER_IR).paint);
		// leftCenter
		canvas.drawCircle(width/2-radius*3, height/8+radius, radius, sensorValues.get(SensorType.LEFT_CENTER_IR).paint);
		// left
		canvas.drawCircle(width/2-radius*4, height/8+2*radius*2, radius, sensorValues.get(SensorType.LEFT_IR).paint);
		// rightCenter
		canvas.drawCircle(width/2+radius*3, height/8+radius, radius, sensorValues.get(SensorType.RIGHT_CENTER_IR).paint);
		// right
		canvas.drawCircle(width/2+radius*4, height/8+2*radius*2, radius, sensorValues.get(SensorType.RIGHT_IR).paint);
		
		// leftEncoder
		canvas.drawCircle(width*3/8, height*3/4, radius, leftEncoderColor);
		// rightEncoder
		canvas.drawCircle(width*5/8, height*3/4, radius, rightEncoderColor);
	}

}

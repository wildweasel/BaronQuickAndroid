package com.example.adkgo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class Controller extends View {
	
	PointF touchPoint;
	private static Paint paint = new Paint(Color.BLUE);

		
	public Controller(Context context, AttributeSet attrs) {
		super(context, attrs);	
	    setBackgroundResource(R.drawable.robot_control_circle);	    	   
		touchPoint = new PointF();
		touchPoint.set(this.getWidth()/2, this.getHeight()/2);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		// We're locked to landscape - always wider than tall - goto square
	    setMeasuredDimension((int)(getMeasuredHeight()*1.15), getMeasuredHeight());
	}
	
	@Override
	public void onDraw(Canvas canvas){
		super.onDraw(canvas);		
		canvas.drawCircle(touchPoint.x, touchPoint.y, 5, paint);		
	}	

	// Handle the touch	
	public void registerTouch(float X, float Y){
			
		// saturate
		if(X < 0) X = 0;
		if(X > this.getWidth()) X = this.getWidth();
		if(Y < 0) Y = 0;
		if(Y > this.getHeight()) Y = this.getHeight();
		
		touchPoint.set(X, Y);
		this.postInvalidate();		
	}
	
	// Return to center	
	public void goHome(){					
		touchPoint.set(this.getWidth()/2, this.getHeight()/2);
		this.postInvalidate();							
	}
	
	// Convert the touch point into a percentage of maximum angular and linear velocity
	// The exploded circle on the screen is basically a polar coordinate plane with a few
	// extra bells and whistles.    The transparent (white) strip down the middle is always
	// zero angular velocity (think of it as stretching out the y-axis a bit prior to polar
	// conversation) This makes it easier to drive the robot straight.  The inner pink (semi)
	// circle are always zero linear velocity (think of it as stretching out the polar origin).
	// This makes it easier to turn the robot in place.
	public PointF calcPolar(){

		// What is the half-circle radius?
		int radius = this.getHeight()/2;
		// What is the inner radius (expanded origin)
		int innerRadius = radius / 5 * 2;
		
		// What are the raw cartesian coordinates of the touch point?  (remember to flip y)
		// The origin is in the dead middle of the window
		float deltaXTouch = touchPoint.x - this.getWidth()/2;
		float deltaYTouch = (this.getHeight() - touchPoint.y) - this.getHeight()/2;
		
		// Account for the expanded y-axis
		deltaXTouch = (float) (Math.signum(deltaXTouch) * Math.max(0, Math.abs(deltaXTouch)-radius*.15));

		// To make a bit more sense practically, let's just mirror the bottom half of the guide
		// to the top.  We'll take the absolute value of the angular velocity and reverse the 
		// sign of the linear velocity.  This could have got weird, but because of the limitations
		// of the real world motors, it makes sense.  When the touch is near the x-axis (i.e. 
		// it's time to 'flip') the angular velocity will be high (or negative of high), and
		// we're limited in how much linear velocity we can have (none when we're sitting on 
		// the x-axis) - so flipping it's sign won't have much actual practical effect
		boolean flipRadius = false;
		if(deltaYTouch < 0){
			deltaYTouch = -deltaYTouch;
			flipRadius = true;
		}
		
		// Convert the touch point to polar coordinates
		double rTouch = Math.sqrt(deltaXTouch*deltaXTouch + deltaYTouch*deltaYTouch);
		double thetaTouch = Math.atan2(deltaYTouch, deltaXTouch);	
		
		// Account for the expanded polar origin
		rTouch = Math.max(0, rTouch-innerRadius);
		
		// Move the pi = 0 polar axis to up direction (instead of right)
		thetaTouch -= Math.PI/2;

		// saturate r to max out at the circle radius (clicks outside get snapped back)
		rTouch = Math.min(rTouch, radius - innerRadius);		
		
		// scale r from 0-deltaradius to 0-1
		float rScale = (float) (rTouch / (radius - innerRadius));
		
		// scale theta from -Pi/2:Pi/2 to 0-1
		float thetaScale = (float) (thetaTouch/Math.PI + .5);
		
		if(flipRadius)
			rScale = -rScale;

		return new PointF(rScale, thetaScale);		
	}
	
}

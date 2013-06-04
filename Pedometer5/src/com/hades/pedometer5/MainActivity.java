/*
 *  I have edited some of the support classes authored by Kirill Morozov.
 *  The classes edited are LineSegment.java and MapView.java.
 */
package com.hades.pedometer5;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements PositionListener {
	
	private MapView map;
	private double rotX = 0;
	private boolean isWall = false;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		/****************** MAP NAME ******************************/
//		final String mapString = "test-room-e2-2356-inclined-9.4deg.svg";
		final String mapString = "Lab-room.svg"; // This is a bonus map
		/**********************************************************/
				
		NavigationalMap mapFile = MapLoader.loadMap(getExternalFilesDir(null), mapString);
//		map = new MapView(getApplicationContext(), 500, 300, 23, 25);
		map = new MapView(getApplicationContext(), 500, 300, 27, 25);
		map.setMap(mapFile);
		registerForContextMenu(map);
		
		RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
		layout.addView(map);
		map.setVisibility(View.VISIBLE);
		
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		/* ROTATION SENSOR */
		Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		class RotationVectorEventListener implements SensorEventListener{

			private float orientationValues[] = new float[3];
			private double x;
			private PointF headingPoint = new PointF();
			private PointF currentPoint = map.getUserPoint();
			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {}

			@Override
			public void onSensorChanged(SensorEvent event) {
				orientationValues[0] = (float) (event.values[0] * (Math.PI/180));
				orientationValues[1] = (float) (event.values[1] * (Math.PI/180));
				orientationValues[2] = (float) (event.values[2] * (Math.PI/180));
				
				x = orientationValues[0];
				setRot(x);
				TextView wallText = (TextView) findViewById(R.id.wallAlert);
				
				//Have I reached the destination?
				PointF userPoint = map.getUserPoint();
				PointF destPoint = map.getDestinationPoint();
				
				if ((userPoint.x >= (destPoint.x - 100)) && (userPoint.x <= (destPoint.x + 100))){
					if ((userPoint.y >= (destPoint.y - 100)) && (userPoint.y <= (destPoint.y + 100))){
						wallText.setText("Destination Reached");
					}
				}
				
				// Show Heading
				List<PointF> list = new ArrayList<PointF>();
				headingPoint.x = (float) (currentPoint.x + Math.sin(orientationValues[0]));
				headingPoint.y = (float) (currentPoint.y + Math.cos(orientationValues[0]));
				list.add(currentPoint);
				list.add(headingPoint);
				map.setHeadingPath(list);
				
				//Am I going to hit a wall? If yes, then don't move. This confines the point within the boundaries of the map.
				List<InterceptPoint> wallComingUp = new ArrayList<InterceptPoint>(); 
				NavigationalMap nm = MapLoader.loadMap(getExternalFilesDir(null), mapString);
				wallComingUp = nm.calculateIntersections(currentPoint,headingPoint);
				if (wallComingUp.size() != 0) wallText.setText("Wall detected!");
				else wallText.setText("No wall. If a path is not detected or is not satisfactory" +
						", turn around in the same spot.");
				if (wallComingUp.size() != 0) isWall = true;  //This condition disables the step counter and accelerometer updates
				else isWall = false;
				
				//This simply checks if the there is a direct path to the destination. If not, it evaluates the else statement.
				List<InterceptPoint> noBlocksIntercept = new ArrayList<InterceptPoint>();
				NavigationalMap noBlocksMap = MapLoader.loadMap(getExternalFilesDir(null), mapString);
				noBlocksIntercept = noBlocksMap.calculateIntersections(map.getUserPoint(), map.getDestinationPoint());
				
				if (noBlocksIntercept.size() == 0){
					List<PointF> noBlocksList = new ArrayList<PointF>();
					noBlocksList.add(map.getUserPoint()); 
					noBlocksList.add(map.getDestinationPoint());
					map.setUserPath2(noBlocksList);
				}
				else{ //This entire block of code under the else statement is my path-finding algorithm
					List<PointF> BlocksList = new ArrayList<PointF>(); // For the drawing the user path
					
					// This block of code extends the vector by a factor of 100
					float[] vector = new float[2];
					float[] newVector = new float[2]; 
					PointF newConvertedPoint = new PointF(); 
					vector[0] = headingPoint.x - map.getUserPoint().x; 
					vector[1] = headingPoint.y - map.getUserPoint().y; 
					newVector = VectorUtils.vectorMult(vector, 100);
					newConvertedPoint.x = newVector[0];
					newConvertedPoint.y = newVector[1]; 
					
					/* Setting the first point in the user path as the intersection
					 * of the wall in which the user is headed.
					 *
					 * My algorithm follows walls. Since the wall is simply a line
					 * segment, I check if there is a direct path free of 
					 * intersections between the endPoint/startPoint of the wall 
					 * and the add that to my list of points to draw a path through.
					 */
					List<InterceptPoint> wallIntercept = new ArrayList<InterceptPoint>();
					NavigationalMap tempMap = MapLoader.loadMap(getExternalFilesDir(null), mapString);
					wallIntercept = tempMap.calculateIntersections(map.getUserPoint(), newConvertedPoint);
					
					PointF intersectionPoint = new PointF();
					LineSegment wallLine = new LineSegment(map.getUserPoint(), map.getDestinationPoint());
					
					intersectionPoint = wallIntercept.get(0).getPoint();
					wallLine = wallIntercept.get(0).getLine();
					
					PointF startPoint = wallLine.getStart();
					PointF endPoint = wallLine.getEnd();
					BlocksList.add(map.getUserPoint());
					BlocksList.add(intersectionPoint);
					
					/* I perform this operation 30 times because I know for fact
					 * that all possibilities are covered by doing so.
					 */
						for (int i = 0; i < 30; i++){
						List<InterceptPoint> listDest = new ArrayList<InterceptPoint>(); 
						listDest = tempMap.calculateIntersections(startPoint, map.getDestinationPoint());
						
						List<InterceptPoint> listDest2 = new ArrayList<InterceptPoint>(); 
						listDest2 = tempMap.calculateIntersections(endPoint, map.getDestinationPoint());
						
						/* The if statements check if a direct path is available.
						 * If it is, it exits out of the loop.
						 */
						if(listDest.size() == 2){ // Checking from startPoint
							BlocksList.add(startPoint);
							BlocksList.add(map.getDestinationPoint());
							break;
						}
						
						if(listDest2.size() == 2){ // Checking from endPoint
							BlocksList.add(endPoint);
							BlocksList.add(map.getDestinationPoint());
							break;
						}
						
						List<LineSegment> list2 = tempMap.getGeometryAtPoint(endPoint);
						PointF point1 = list2.get(0).getStart();
						PointF point2 = list2.get(0).getEnd();
						PointF point3 = list2.get(1).getStart();
						PointF point4 = list2.get(1).getEnd();
						
						if (point1.equals(point4)){
							startPoint = point2;
							endPoint = point3;
						}
						else{
							startPoint = point1;
							endPoint = point4;
						}
					}
					/* This sends the list of points to be drawn on the map. This
					 * is a secondary path and not the heading.
					 */
					map.setUserPath2(BlocksList);
					
				}
			}		
		}

		SensorEventListener rot = new RotationVectorEventListener();
		sensorManager.registerListener(rot, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);
		/* END OF ROTATION */ 
		
		/* ACCELERATION SENSOR */
		Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		class AccelerometerEventListener implements SensorEventListener{
			
			private float[] fixedValues = new float[3];
			private double z = 0;
			private double temp = 0;
			private int steps = -1;
			private double tempSteps = 0;
			private double orientationValue = 0;
			private double range = 0.050d;
			private float constant2 = 0.5f;
			
			PointF newPoint = map.getUserPoint();
			
			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {}

			@Override
			public void onSensorChanged(SensorEvent event) {
				if ( isWall == false){
					final float constant = (float) 0.05; 
					
					fixedValues[0] = constant * fixedValues[0] + (1 - constant) * event.values[0];
					fixedValues[1] = constant * fixedValues[1] + (1 - constant) * event.values[1];
					fixedValues[2] = constant * fixedValues[2] + (1 - constant) * event.values[2];
					
					z = event.values[2] - fixedValues[2];
					
					if ((z - temp) > range) steps++;
					
					temp = z;
					orientationValue = rotX;
					
					/* The logic here update the userPoint on the map, when a step is recorded.
					 * This is done with respect to direction.
					 */
					if (steps > tempSteps){
						
						if ((orientationValue >= 0) && (orientationValue < (Math.PI / 2))){
							newPoint.y += (constant2 * Math.cos(orientationValue));
							newPoint.x += (constant2 * Math.sin(orientationValue));
						}
						else if((orientationValue >= (Math.PI/2)) && (orientationValue < Math.PI)){
							newPoint.y -= (constant2 * Math.sin(orientationValue - (Math.PI/2)));
							newPoint.x += (constant2 * Math.cos(orientationValue - (Math.PI/2)));
						}
						else if((orientationValue >= (Math.PI)) && (orientationValue < (3 * (Math.PI/2)))){
							newPoint.y -= (constant2 * Math.cos(orientationValue - Math.PI));
							newPoint.x  -= (constant2 * Math.sin(orientationValue - Math.PI));
						}
						else if((orientationValue >= (3 * (Math.PI/2)))){
							newPoint.y += (constant2 * Math.sin(orientationValue - (3 * (Math.PI/2))));
							newPoint.x  -= (constant2 * Math.cos(orientationValue - (3 * (Math.PI/2))));
						}
						// Setting userPoint
						map.setUserPoint(newPoint);
					}
					tempSteps = steps; 
				}
			}
		}
		SensorEventListener accel = new AccelerometerEventListener();
		sensorManager.registerListener(accel, accelerometer,
				sensorManager.SENSOR_DELAY_NORMAL);
		/* END OF ACCELERATION */
		
		// Mapping
		map.addListener(this);
	}
	
	//setter
	private void setRot(double x){
		this.rotX = x;
	}
	
	@Override
	public void onCreateContextMenu (ContextMenu menu, View v, ContextMenuInfo menuInfo){
		super.onCreateContextMenu(menu, v, menuInfo);
		map.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item){
		return super.onContextItemSelected(item) || map.onContextItemSelected(item);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	// The originChanged method sets the origin (from PositionListener)
	@Override
	public void originChanged(MapView source, PointF loc) {
		source.setUserPoint(loc);
	}
	
	// The destinationChanged method sets the destination (from PositionListener)
	@Override
	public void destinationChanged(MapView source, PointF dest) {
		source.setDestinationPoint(dest);
	}

}

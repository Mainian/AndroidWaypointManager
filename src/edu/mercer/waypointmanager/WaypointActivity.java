package edu.mercer.waypointmanager;

import android.support.v4.app.FragmentActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


//Google imports
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class WaypointActivity extends FragmentActivity
		implements
		OnConnectionFailedListener,
		OnMyLocationButtonClickListener,
		com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks,
		com.google.android.gms.location.LocationListener,
		SensorEventListener {

	//Map & Location Objects
	private GoogleMap mMap;
	private Marker mMarker;
	private LocationClient mLocationClient;
	private TextView distanceText;
	private ImageView arrowImageView;
	
	//store arrow as bitmap
	private Bitmap arrowBitMap;
	float directionForRotation = (float)0.0;

	// Target Location Information
	private double latTarget = 0.0;
	private double longTarget = 0.0;
	private Location targetLocation;
	private LatLng targetLatLng = new LatLng(0.0,0.0);
	private boolean haveTarget = false;

	//Boolean determining if we should auto update distance // rotate arrow
	private boolean doingNavigation = false;
	
	//Persistent Data Section
	SharedPreferences prefs;
	String latKey = "";
	String longKey = "";
	
	//SensorManger Information - Arrow / Heading Section
	SensorManager mSensorManager;

	// These settings are the same as the settings for the map. They will in
	// fact give you updates
	// at the maximal rates currently possible.
	private static final LocationRequest REQUEST = LocationRequest.create()
			.setInterval(5000) // 5 seconds
			.setFastestInterval(16) // 16ms = 60fps
			.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_waypoint);
		
		//Pull keys and shared preferences
		Context context = getApplicationContext();
		latKey = context.getString(R.string.latKeyData);
		longKey = context.getString(R.string.longKeyData);
		prefs = this.getSharedPreferences("edu.mercer.waypointmanager", Context.MODE_PRIVATE);
		
		/* Pull data from shared preferences
		 * the default values are outside of gps ranges*/
		float storedLat = prefs.getFloat(latKey, (float) 400.0);
		float storedLong = prefs.getFloat(longKey, (float) 400.0);
		
		if(storedLat < (float) 400.0 && storedLong < (float) 400.0)
		{
			haveTarget = true;
			
			//we can't poll target location currently - because our map hasn't been setup yet
			latTarget = storedLat;
			longTarget = storedLong;
			targetLatLng = new LatLng(latTarget, longTarget);
		}
		
		//Grab textview
		distanceText = (TextView) findViewById(R.id.distanceText);
		
		//grab imageview
		arrowImageView = (ImageView) findViewById(R.id.arrowNav);		

		//We are going to be rotating this bitmap a lot - store it
		arrowBitMap = BitmapFactory.decodeResource(context.getResources(), R.drawable.arrow);
		
		//Grab sensor manager
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		setUpMapIfNeeded();
	}

	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.waypoint, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
		setUpLocationClientIfNeeded();
		
		//Callback methods
		mLocationClient.connect();
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mLocationClient != null) {
			mLocationClient.disconnect();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menuButtonSet) {
			saveLocation();
			doingNavigation = false;
			//stopTimer();
			
		} else if (id == R.id.menuButtonNav) {
			if (targetLocation == null || doingNavigation)
				return true;
			doingNavigation = true;
		} else if (id == R.id.menuButtonNavStop) {
			if (targetLocation == null || !doingNavigation)
				return true;
			
			doingNavigation = false;
			//stopTimer();
		} else if (id == R.id.menuButtonReset)
		{
			clearLocationData();
			doingNavigation = false;
			//stopTimer();
		}
		return super.onOptionsItemSelected(item);
	}

	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the
		// map.
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = ((SupportMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.map)).getMap();
			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				setUpMap();
			}
		}
	}

	private void setUpMap() {
		mMap.setMyLocationEnabled(true);
		mMap.setOnMyLocationButtonClickListener(this);
	}

	private void setUpLocationClientIfNeeded() {
		if (mLocationClient == null) {
			mLocationClient = new LocationClient(getApplicationContext(), this, // ConnectionCallbacks
					this); // OnConnectionFailedListener
		}
	}
	
	private void saveLocation()
	{
		// save current location as a target. 
		targetLocation = mLocationClient.getLastLocation();
		latTarget = targetLocation.getLatitude();
		longTarget = targetLocation.getLongitude();
		targetLatLng = new LatLng(latTarget, longTarget);
		
		//Add current location as Marker. Remove an existing marker
		if(mMarker != null)
			mMarker.remove();
		
		mMarker = mMap.addMarker(new MarkerOptions()
							.position(targetLatLng)
							.title("Target"));
		
		saveLocationToPreferences((float)latTarget, (float)longTarget);
	}
	
	private void performNavigation(Location loca)
	{
		//This needs to update our distance / target to get distance
		float distance = loca.distanceTo(targetLocation);
		
		if(distance < (float)1.0)
		{
			//stop navigation
			doingNavigation = false;
			Toast.makeText(this, getResources().getString(R.string.arrivedAtLocation), Toast.LENGTH_LONG).show();
			distanceText.setText("");
			arrowImageView.setImageDrawable(getResources().getDrawable(R.drawable.arrow));
			return;
		}
		
		String text = String.valueOf(distance);
		distanceText.setText(text);
	}
	
	
	private void saveLocationToPreferences(float latitude, float longitude)
	{
		//grab shared preferences editor
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.clear();
		editor.putFloat(latKey, latitude);
		editor.putFloat(longKey, longitude);
		editor.commit();
	}
	
	private void clearLocationData()
	{
		if(mMarker != null)
		{
			mMarker.remove();
		}
		
		doingNavigation = false;
		distanceText.setText("");
		
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.clear();
		editor.commit();
	}
	
	private void rotateArrowImage(float rotate)
	{
		int width = arrowBitMap.getWidth();
		int height = arrowBitMap.getHeight();
		
		Matrix matrix = new Matrix();
		rotate = rotate % 360;
		matrix.postRotate( rotate, width, height);
		Bitmap updatedArrow = Bitmap.createBitmap(arrowBitMap, 0, 0, width, height, matrix, true);
		
		arrowImageView.setImageDrawable(new BitmapDrawable(getApplicationContext().getResources(), updatedArrow));
	}
	


	/**
	 * Callback called when connected to GCore. Implementation of
	 * {@link ConnectionCallbacks}.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		mLocationClient.requestLocationUpdates(REQUEST, this); // LocationListener

		// get long and lat of current location
		Location last = mLocationClient.getLastLocation();
		double longitude = last.getLongitude();
		double latitude = last.getLatitude();

		//change map to look at current location
		LatLng loc = new LatLng(latitude, longitude);
		mMap.moveCamera(CameraUpdateFactory.zoomTo((float) 15.0));
		mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
		
		//if we have stored data / persistent data
		if(haveTarget)
		{
			targetLocation = last;
			//for now all we care about is lat and long
			targetLocation.setLatitude(latTarget);
			targetLocation.setLongitude(longTarget);
			
			//add marker
			mMarker = mMap.addMarker(new MarkerOptions()
			.position(targetLatLng)
			.title("Target"));
		}
	}



	
	@Override 
	public void onSensorChanged(SensorEvent event) {
		
		if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
			return;
		
		if(!doingNavigation || targetLocation == null)
			return;
		
		//event returns a float[3]. event[0]=azimuth, event[1]=pitch, event[2]=roll; 
		float azimuth = event.values[0];
		
		//get previous location - current location could be changing
		Location loca = mLocationClient.getLastLocation();
		float lat = (float)loca.getLatitude();
		float longi = (float)loca.getLongitude();
		float altitude = (float)loca.getAltitude();
		
		GeomagneticField geoField = new GeomagneticField(lat, longi, altitude, System.currentTimeMillis());
		
		azimuth -= geoField.getDeclination();
		
		float bearingToTarget = loca.bearingTo(targetLocation);
		
		if(bearingToTarget < 0)
			bearingToTarget += 360;
		
		float direction = bearingToTarget - azimuth;
		
		if(direction < 0)
		{
			direction = direction + 360;
		}
		
		directionForRotation = direction;
	}
	
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		
	}

	@Override
	public boolean onMyLocationButtonClick() {
		return false;
	}

	@Override
	public void onLocationChanged(Location arg0) {
		if(doingNavigation && targetLocation !=null)
		{
			performNavigation(arg0);
			rotateArrowImage(directionForRotation);
		}
	}

	@Override
	public void onDisconnected() {
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}

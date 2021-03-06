package me.hoen.android_mock_gps;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

public class MockLocationProvider extends Service implements LocationListener,
		ConnectionCallbacks, OnConnectionFailedListener, ResultCallback<Status> {

	public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
	public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 5;
	protected GoogleApiClient mGoogleApiClient;
	protected LocationRequest mLocationRequest;

	private static final int GPS_START_INTERVAL = 500;
	private ArrayList<ArrayList<Geoloc>> data = new ArrayList<>();
	private ArrayList<Location> pathPoints = new ArrayList<>();
	private ArrayList<Double> bearingDegrees = new ArrayList<>();
	private LocationManager locationManager;
	private String mockLocationProvider = "gps";

	public static final int NOTIFICATION_ID = 42;

	public static final String SERVICE_STOP = "me.hoen.android_mock_gps.STOP";
	public static final String SERVICE_PAUSE = "me.hoen.android_mock_gps.PAUSE";
	public static final String SERVICE_PLAY = "me.hoen.android_mock_gps.PLAY";
	public static final String SERVICE_REWIND = "me.hoen.android_mock_gps.REWIND";
	public static final String SERVICE_SET_SPEED = "me.hoen.android_mock_gps.SETSPEED";
	public static final String PREVIOUS_ROAD = "me.hoen.android_mock_gps.PREVIOUS";
	public static final String MOVE_ROAD = "me.hoen.android_mock_gps.MOVE";
	public static final String NEXT_ROAD = "me.hoen.android_mock_gps.NEXT";

	private String filename = "";
	private int speed = 40;		//km/h
	private int routeIndex = 0;

	private boolean isPlaying = false;

	private int currentIndex = 0;

	private double latOffset = 0;
	private double longOffset = 0;
	private int latSign = 1;
	private int longSign = 1;

	private float degree = 0;

	private Location currentLocation = new Location("Point");
	private ArrayList<Double> maxSpeeds = new ArrayList<>();
	private ArrayList<Integer> lanes = new ArrayList<>();
	private ArrayList<String> roads = new ArrayList<>();

	protected BroadcastReceiver stopServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_STOP)) {

				data.clear();
				isPlaying = false;
				MockLocationProvider.this.stopSelf();
			}
		}
	};

	protected BroadcastReceiver rewindServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_REWIND)) {
				currentLocation.setLatitude(currentLocation.getLatitude() - latSign * latOffset * 20);
				currentLocation.setLongitude(currentLocation.getLongitude() - longSign * longOffset * 20);
			}
		}
	};

	protected BroadcastReceiver setSpeedServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_SET_SPEED)) {
				speed = intent.getIntExtra("speed", 0);
				calculateOffset();
			}
		}
	};

	protected BroadcastReceiver pauseServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_PAUSE)) {
				isPlaying = false;
				handler.removeCallbacksAndMessages(null);
				Log.d(MainActivity.TAG, "Mock GPS paused");
			}
		}
	};

	protected BroadcastReceiver playServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_PLAY)) {
				isPlaying = true;
				handler.post(runnable);
				Log.d(MainActivity.TAG, "Mock GPS Play");
			}
		}
	};

	protected BroadcastReceiver previousRoadReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(PREVIOUS_ROAD)) {
				routeIndex--;
				currentIndex = 0;
				if(routeIndex < 0) {
					routeIndex = 0;
				}
				currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
				currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
				calculateOffset();
			}
		}
	};

	protected BroadcastReceiver nextRoadReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(NEXT_ROAD)) {
				routeIndex++;
				currentIndex = 0;
				if(routeIndex >= data.size() - 1) {
					routeIndex = data.size() - 1;
				}
				currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
				currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
				calculateOffset();
			}
		}
	};

	protected BroadcastReceiver moveRoadReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(MOVE_ROAD)) {
				int route = intent.getIntExtra("route", 1);
				routeIndex = route - 1;
				currentIndex = 0;
				currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
				currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
				calculateOffset();
			}
		}
	};

	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		super.onCreate();

		Log.d(MainActivity.TAG, "Mock GPS started");
		buildGoogleApiClient();
		mGoogleApiClient.connect();

		registerReceiver(stopServiceReceiver, new IntentFilter(SERVICE_STOP));
		registerReceiver(pauseServiceReceiver, new IntentFilter(SERVICE_PAUSE));
		registerReceiver(playServiceReceiver, new IntentFilter(SERVICE_PLAY));
		registerReceiver(rewindServiceReceiver, new IntentFilter(SERVICE_REWIND));
		registerReceiver(setSpeedServiceReceiver, new IntentFilter(SERVICE_SET_SPEED));
		registerReceiver(previousRoadReceiver, new IntentFilter(PREVIOUS_ROAD));
		registerReceiver(moveRoadReceiver, new IntentFilter(MOVE_ROAD));
		registerReceiver(nextRoadReceiver, new IntentFilter(NEXT_ROAD));
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		try {
			locationManager.addTestProvider("gps", false, false,
					false, false, true, true, true, 0, 5);
			locationManager.setTestProviderEnabled("gps", true);
		} catch (Exception ex) {
			locationManager.removeTestProvider("gps");
			locationManager.addTestProvider("gps", false, false,
					false, false, true, true, true, 0, 5);
			locationManager.setTestProviderEnabled("gps", true);
		}
	}

	protected synchronized void buildGoogleApiClient() {
		Log.i(MainActivity.TAG, "Building GoogleApiClient");
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API).build();
		createLocationRequest();
	}

	protected void createLocationRequest() {
		mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
		mLocationRequest
				.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	private void initGpsLatLng() {
		StringBuilder stringBuilder = new StringBuilder();
		InputStream is = null;
		String UTF8 = "utf8";
		int BUFFER_SIZE = 8192;
		data.clear();
		pathPoints.clear();
		bearingDegrees.clear();
		roads.clear();
		try {
			is = getContentResolver().openInputStream(Uri.parse(filename));
			String string = "";
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF8), BUFFER_SIZE);
			while (true) {
				try {
					if ((string = reader.readLine()) == null) break;
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				stringBuilder.append(string).append("\n");
			}
			is.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.v("String Reader", stringBuilder.toString());
		try {
			JSONObject geoObject = new JSONObject(stringBuilder.toString());
			JSONArray features = geoObject.getJSONArray("features");
			if(features.length() > 0) {
				maxSpeeds.clear();
				lanes.clear();
				for(int k = 0; k < features.length(); k++) {
					JSONObject feature = features.getJSONObject(k);
					JSONObject geometry = feature.getJSONObject("geometry");
					JSONArray coordiantesParent = geometry.getJSONArray("coordinates");
					String type = geometry.getString("type");
					JSONObject properties = feature.getJSONObject("properties");
					double maxSpeed = properties.getDouble("Max_Speed");
					int lane = properties.getInt("LANES");
					String roadname = properties.getString("ROAD_NAME");

					ArrayList<Geoloc> routePoints = new ArrayList<>();
					if(type.equals("MultiLineString")) {
						for(int j = 0; j < coordiantesParent.length(); j++) {
							JSONArray coordinates = coordiantesParent.getJSONArray(j);
							for(int i = 0; i < coordinates.length(); i++) {
								JSONArray itemArray = new JSONArray(coordinates.getString(i));
								Log.v("Lat", String.valueOf(itemArray.getDouble(0)));
								Log.v("Lat", String.valueOf(itemArray.getDouble(1)));
								routePoints.add(new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 10, 5));
							}
							data.add(routePoints);
							lanes.add(lane);
							maxSpeeds.add(maxSpeed);
							roads.add(roadname);
						}
					} else {
						for(int i = 0; i < coordiantesParent.length(); i++) {
							JSONArray itemArray = new JSONArray(coordiantesParent.getString(i));
							routePoints.add(new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 10, 5));
						}
						maxSpeeds.add(maxSpeed);
						lanes.add(lane);
						data.add(routePoints);
						roads.add(roadname);
					}
				}
				routeIndex = 0;
				currentIndex = 0;
				currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
				currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
				calculateOffset();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		isPlaying = true;
	}

	private void calculateOffset() {
		if(currentIndex < data.get(routeIndex).size() - 1) {
			Geoloc startLocation = data.get(routeIndex).get(currentIndex);
			Geoloc endLocation = data.get(routeIndex).get(currentIndex + 1);
			degree = calculateBearingDegree(startLocation.latitude, startLocation.longitude, endLocation.latitude, endLocation.longitude);
			double distance = calculateDistance(startLocation.latitude, startLocation.longitude, endLocation.latitude, endLocation.longitude);
			double speedPerSec = speed * 1000 / 3600.0f / 10;
			Log.v("Speed Per Sec", String.valueOf(speedPerSec));
			int totalTime = (int)(Math.ceil(distance / speedPerSec));
			Log.v("Time", String.valueOf(totalTime));
			Log.v("Index", String.valueOf(currentIndex));
			if(totalTime > 0) {
				latOffset = Math.abs((endLocation.latitude - startLocation.latitude) / totalTime);
				longOffset = Math.abs((endLocation.longitude - startLocation.longitude) / totalTime);
			}
			if(endLocation.latitude >= startLocation.latitude) {
				latSign = 1;
			} else {
				latSign = -1;
			}
			if(endLocation.longitude >= startLocation.longitude) {
				longSign = 1;
			} else {
				longSign = -1;
			}
			Log.v("LatOffset", String.valueOf(latOffset));
			Log.v("LongOffset", String.valueOf(longOffset));
			Log.v("LatSign", String.valueOf(latSign));
			Log.v("LongSign", String.valueOf(longSign));
		}
	}

	Handler handler = new Handler();
	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			if(isPlaying) {
				if(routeIndex >= data.size()) {
					handler.removeCallbacksAndMessages(null);
					return;
				}
				if(currentIndex < data.get(routeIndex).size() - 1) {
					Geoloc endLocation = data.get(routeIndex).get(currentIndex + 1);
					if(routeIndex == data.size()) {
						if(latSign == 1 && currentLocation.getLatitude() >= endLocation.latitude) {
							handler.removeCallbacksAndMessages(null);
						} else if(latSign == -1 && currentLocation.getLatitude() <= endLocation.latitude) {
							handler.removeCallbacksAndMessages(null);
						}
					} else {
						if(latSign == 1 && currentLocation.getLatitude() >= endLocation.latitude) {
							currentIndex++;
							currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
							currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
							calculateOffset();
						} else if(latSign == -1 && currentLocation.getLatitude() <= endLocation.latitude) {
							currentIndex++;
							currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
							currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
							calculateOffset();
						}
					}
				} else {
					currentIndex = 0;
					routeIndex++;
					if(routeIndex >= data.size()) {
						handler.removeCallbacksAndMessages(null);
						return;
					}
					currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
					currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
					calculateOffset();
				}

				sendLocation();
				handler.postDelayed(runnable, 100);
			}
		}
	};

	@SuppressLint("NewApi")
	private void sendLocation() {
		Location g = currentLocation; //pathPoints.get(i);

		Location location = new Location(mockLocationProvider);
		location.setLatitude(g.getLatitude());
		location.setLongitude(g.getLongitude());
		location.setAltitude(0);
		location.setAccuracy(5);
		location.setBearing(degree);
		float speedPerSec = speed * 1000 / 3600.0f;
		location.setSpeed(speedPerSec);
		location.setTime(System.currentTimeMillis());
		if (android.os.Build.VERSION.SDK_INT >= 17) {
			location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}
		locationManager.setTestProviderLocation(mockLocationProvider, location);
		LocationServices.FusedLocationApi.setMockLocation(mGoogleApiClient, location);
		Intent locationReceivedIntent = new Intent(
				MockGpsFragment.LOCATION_RECEIVED);
		Geoloc geo = new Geoloc(location.getLatitude(), location.getLongitude(), 5, 10);
		locationReceivedIntent.putExtra("geoloc", geo);
		locationReceivedIntent.putExtra("degree", degree);
		locationReceivedIntent.putExtra("lanes", lanes.get(routeIndex));
		locationReceivedIntent.putExtra("max_speed", maxSpeeds.get(routeIndex));
		locationReceivedIntent.putExtra("route_number", routeIndex);
		locationReceivedIntent.putExtra("road_name", roads.get(routeIndex));
		sendBroadcast(locationReceivedIntent);

		currentLocation.setLatitude(currentLocation.getLatitude() + latSign * latOffset);
		currentLocation.setLongitude(currentLocation.getLongitude() + longSign * longOffset);
	}

	protected void displayStartNotification() {
		NotificationManager notificationManager = (NotificationManager) this
				.getSystemService(Context.NOTIFICATION_SERVICE);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				this)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(
						getString(R.string.start_mock_gps_notification_message))
				.setAutoCancel(false);

		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.putExtra("performAction", "stopMockGps");
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(contentIntent);

		Notification notification = mBuilder.build();
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		notificationManager.notify(NOTIFICATION_ID, notification);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		filename = intent.getStringExtra("file_name");
		speed = intent.getIntExtra("speed", 40);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v("Broadcast", "Paused");
		unregisterReceiver(stopServiceReceiver);
		unregisterReceiver(playServiceReceiver);
		unregisterReceiver(pauseServiceReceiver);
		unregisterReceiver(rewindServiceReceiver);
		unregisterReceiver(setSpeedServiceReceiver);
		unregisterReceiver(previousRoadReceiver);
		unregisterReceiver(moveRoadReceiver);
		unregisterReceiver(nextRoadReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.v("Broadcast", "Binded");
		return null;
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		// Log.d("lstech.aos.debug", "Service -> geoloc failed");
	}

	private double calculateDistance(double lat1, double long1, double lat2, double long2) {		// Calculate Distance in Meters
		Location locationA = new Location("point A");
		locationA.setLatitude(lat1);
		locationA.setLongitude(long1);
		Location locationB = new Location("point B");
		locationB.setLatitude(lat2);
		locationB.setLongitude(long2);

		double distance = locationA.distanceTo(locationB);
		return distance;
	}

	private float calculateBearingDegree(double lat1, double long1, double lat2, double long2) {		//Calculate heading degree
		Location locationA = new Location("point A");
		locationA.setLatitude(lat1);
		locationA.setLongitude(long1);
		Location locationB = new Location("point B");
		locationB.setLatitude(lat2);
		locationB.setLongitude(long2);

		float bearingTo = locationA.bearingTo(locationB);
		return bearingTo;
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		Log.d("lstech.aos.debug", "Service -> geoloc connected");
		LocationServices.FusedLocationApi.setMockMode(mGoogleApiClient, true);
		new AsyncTask<String, Integer, String>() {
			@Override
			protected String doInBackground(String... params) {
				initGpsLatLng();
				return null;
			}

			@Override
			protected void onPostExecute(String result) {
				displayStartNotification();
				if(data.size() > 0) {
					handler.post(runnable);
				}
				super.onPostExecute(result);
			}
		}.execute("");

	}

	@Override
	public void onLocationChanged(Location location) {
		Log.d(MainActivity.TAG, "Speed : "
				+ String.valueOf(location.getSpeed()));
	}

	protected void retrieveNearbyData() {
	}

	@Override
	public void onResult(Status result) {

	}

	@Override
	public void onConnectionSuspended(int cause) {
		mGoogleApiClient.connect();
	}
}
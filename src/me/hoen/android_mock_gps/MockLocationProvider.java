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

	protected BroadcastReceiver stopServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_STOP)) {

				data.clear();
				isPlaying = false;
				MockLocationProvider.this.stopSelf();

				Log.d(MainActivity.TAG, "Mock GPS stopped");
			}
		}
	};

	protected BroadcastReceiver rewindServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_REWIND)) {
				currentLocation.setLatitude(currentLocation.getLatitude() - latSign * latOffset * 2);
				currentLocation.setLongitude(currentLocation.getLongitude() - longSign * longOffset * 2);
			}
		}
	};

	protected BroadcastReceiver setSpeedServiceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SERVICE_SET_SPEED)) {
				speed = intent.getIntExtra("speed", 0);
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
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if(locationManager.getProvider("mock_provider") != null) {
			locationManager.removeTestProvider("mock_provider");
		}
		locationManager.addTestProvider("mock_provider", false, false,
				false, false, true, true, true, 0, 5);
		locationManager.setTestProviderEnabled("mock_provider", true);
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
		Log.v("File Name", filename);
		StringBuilder stringBuilder = new StringBuilder();
		InputStream is = null;
		String UTF8 = "utf8";
		int BUFFER_SIZE = 8192;
		data.clear();
		pathPoints.clear();
		bearingDegrees.clear();
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
				for(int k = 0; k < features.length(); k++) {
					JSONObject feature = features.getJSONObject(k);
					JSONObject geometry = feature.getJSONObject("geometry");
					JSONArray coordiantes = geometry.getJSONArray("coordinates");
					ArrayList<Geoloc> routePoints = new ArrayList<>();
					for(int i = 0; i < coordiantes.length(); i++) {
						JSONArray itemArray = new JSONArray(coordiantes.getString(i));
						routePoints.add(new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 10, 5));
					}
					data.add(routePoints);
					/*for(int i = 0; i < data.size() - 1; i++) {
						Geoloc firstPoint = data.get(i);
						Geoloc secondPoint = data.get(i + 1);
						double distance = calculateDistance(firstPoint.latitude, firstPoint.longitude, secondPoint.latitude, secondPoint.longitude);
						double speedPerSec = speed * 1000 / 3600.0f;
						Log.v("Speed Per Sec", String.valueOf(speedPerSec));
						int totalTime = (int)(Math.ceil(distance / speedPerSec));
						double latOffset = (secondPoint.latitude - firstPoint.latitude) / totalTime;
						double longOffset = (secondPoint.longitude - firstPoint.longitude) / totalTime;
						Log.v("Offset", latOffset + "," + longOffset);
						double firstLat = firstPoint.latitude;
						double firstLong = firstPoint.longitude;
						for(int j = 0; j < totalTime; j++) {
							Location location = new Location("Point");
							location.setLatitude(firstLat);
							location.setLongitude(firstLong);
							double degree = calculateBearingDegree(firstLat, firstLong, firstLat + latOffset, firstLong + longOffset);
							bearingDegrees.add(degree);
							pathPoints.add(location);
							firstLat += latOffset;
							firstLong += longOffset;
						}
					}*/
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
			double speedPerSec = speed * 1000 / 3600.0f;
			Log.v("Speed Per Sec", String.valueOf(speedPerSec));
			int totalTime = (int)(Math.ceil(distance / speedPerSec));
			latOffset = Math.abs((endLocation.latitude - startLocation.latitude) / totalTime);
			longOffset = Math.abs((endLocation.longitude - startLocation.longitude) / totalTime);
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
					currentLocation.setLatitude(data.get(routeIndex).get(currentIndex).latitude);
					currentLocation.setLongitude(data.get(routeIndex).get(currentIndex).longitude);
					calculateOffset();
				}

				sendLocation();
				handler.postDelayed(runnable, 1000);
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
		location.setSpeed(g.getSpeed());
		location.setTime(System.currentTimeMillis());
		if (android.os.Build.VERSION.SDK_INT >= 17) {
			location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}
		Log.v("Current Latitude", String.valueOf(location.getLatitude()));
		Log.v("Current Longitude", String.valueOf(location.getLongitude()));
		locationManager.setTestProviderLocation(mockLocationProvider, location);
		LocationServices.FusedLocationApi.setMockLocation(mGoogleApiClient, location);
		Intent locationReceivedIntent = new Intent(
				MockGpsFragment.LOCATION_RECEIVED);
		Geoloc geo = new Geoloc(location.getLatitude(), location.getLongitude(), 5, 10);
		locationReceivedIntent.putExtra("geoloc", geo);
		locationReceivedIntent.putExtra("degree", degree);
		Log.v("Degree", String.valueOf(degree));
		//locationReceivedIntent.putExtra("long", g.longitude);
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
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(stopServiceReceiver);
		unregisterReceiver(playServiceReceiver);
		unregisterReceiver(pauseServiceReceiver);
		unregisterReceiver(rewindServiceReceiver);
		unregisterReceiver(setSpeedServiceReceiver);
	}

	@Override
	public IBinder onBind(Intent intent) {
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
				handler.post(runnable);
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
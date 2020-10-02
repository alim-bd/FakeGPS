package me.hoen.android_mock_gps;

import java.util.ArrayList;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MockGpsFragment extends Fragment implements LocationListener {
	protected LocationManager locationManager;

	protected MapView mapView;
	protected IMapController mapController;
	protected PathOverlay path;

	protected ArrayList<Integer> geolocIndexes = new ArrayList<Integer>();

	public static final String LOCATION_RECEIVED = "me.hoen.android_mock_gps.LOCATION_RECEIVED";
	protected BroadcastReceiver locationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(LOCATION_RECEIVED)) {
				int i = intent.getIntExtra("geolocIndex", 0);

				if (geolocIndexes.size() == 0
						|| geolocIndexes.get(geolocIndexes.size() - 1) != i) {
					Geoloc g = GeolocStore.getInstance().getGeolocs().get(i);
					receiveLocation(g);

					geolocIndexes.add(i);
				}
			}
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_mock_gps, container,
				false);

		Button startMockGpsBt = (Button) rootView
				.findViewById(R.id.start_mock_gps);
		startMockGpsBt.setOnClickListener(getStartMockGpsListener());

        Configuration.getInstance().setUserAgentValue(getActivity().getPackageName());


        mapView = (MapView) rootView.findViewById(R.id.mapview);
		mapView.setTileSource(TileSourceFactory.MAPNIK);
		mapView.setTilesScaledToDpi(true);
		mapView.setMultiTouchControls(true);

		path = new PathOverlay(Color.YELLOW, getActivity());
		mapView.getOverlays().add(path);

		MyLocationNewOverlay myLocationNewOverlay = new MyLocationNewOverlay(mapView);
		myLocationNewOverlay.enableFollowLocation();
		myLocationNewOverlay.enableMyLocation();
		mapView.getOverlays().add(myLocationNewOverlay);

		Paint paint = path.getPaint();
		paint.setStrokeWidth(5);
		path.setPaint(paint);

		mapController = mapView.getController();
		mapController.setCenter(new GeoPoint(48.860553, 2.339678));
		mapController.setZoom(12);

		return rootView;
	}

	@Override
	public void onDestroy() {
		getActivity().sendBroadcast(new Intent("GP_PROVIDER_STOP_SERVICE"));
		if (locationManager != null) {
			locationManager.removeUpdates(this);
		}
		super.onDestroy();
	}

	@Override
	public void onPause() {
		super.onPause();

		getActivity().unregisterReceiver(locationReceiver);
	}

	@Override
	public void onResume() {
		super.onResume();

		getActivity().registerReceiver(locationReceiver,
				new IntentFilter(LOCATION_RECEIVED));
	}

	@Override
	public void onLocationChanged(Location location) {
		Log.d(MainActivity.TAG, "location: " + location);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {

	}

	@Override
	public void onProviderEnabled(String provider) {

	}

	@Override
	public void onProviderDisabled(String provider) {

	}

	protected void addMarker(Geoloc g) {
		GeoPoint geoPoint = new GeoPoint(g.getLatitude(), g.getLongitude());

		Marker marker = new Marker(mapView);
		marker.setPosition(geoPoint);
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		marker.setRelatedObject(g);
		//mapView.getOverlays().add(marker);

		path.addPoint(geoPoint);

		mapController.setZoom(12);
		mapController.setCenter(geoPoint);
	}

	protected void receiveLocation(Geoloc g) {
		addMarker(g);
	}

	protected View.OnClickListener getStartMockGpsListener() {
		return new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), MockLocationProvider.class);
				intent.putExtra("file_name", "route.csv");
				getActivity().startService(intent);
			}
		};
	}
}

package me.hoen.android_mock_gps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.cocoahero.android.geojson.GeoJSON;
import com.cocoahero.android.geojson.GeoJSONObject;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.obsez.android.lib.filechooser.internals.FileUtil;
import com.opencsv.CSVReader;

import static android.app.Activity.RESULT_OK;

public class MockGpsFragment extends Fragment implements LocationListener {
	protected LocationManager locationManager;

	protected MapView mapView;
	protected IMapController mapController;
	protected PathOverlay path;

	protected ArrayList<Integer> geolocIndexes = new ArrayList<Integer>();
	private Button btnSelectRoute;
	private String routeFilePath = "";


	public static final String LOCATION_RECEIVED = "me.hoen.android_mock_gps.LOCATION_RECEIVED";
	protected BroadcastReceiver locationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(LOCATION_RECEIVED)) {
				Geoloc g = (Geoloc) intent.getSerializableExtra("geoloc");
				receiveLocation(g);
			}
		}
	};

	private List<GeoPoint> pts = new ArrayList<>();

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_mock_gps, container,
				false);

		ImageButton startMockGpsBt = (ImageButton) rootView
				.findViewById(R.id.start_mock_gps);

		btnSelectRoute = rootView.findViewById(R.id.btn_open_route);
		startMockGpsBt.setOnClickListener(getStartMockGpsListener());

        Configuration.getInstance().setUserAgentValue(getActivity().getPackageName());

		String ext = FileUtil.getStoragePath(getActivity(), true);
		String itl = FileUtil.getStoragePath(getActivity(), false);

        mapView = (MapView) rootView.findViewById(R.id.mapview);
		mapView.setTileSource(TileSourceFactory.MAPNIK);
		mapView.setTilesScaledToDpi(true);
		mapView.setMultiTouchControls(true);

		path = new PathOverlay(Color.YELLOW, getActivity());
		mapView.getOverlays().add(path);

		MyLocationNewOverlay myLocationNewOverlay = new MyLocationNewOverlay(mapView);
		myLocationNewOverlay.enableMyLocation();
		mapView.getOverlays().add(myLocationNewOverlay);

		Paint paint = path.getPaint();
		paint.setStrokeWidth(20);
		path.setPaint(paint);
		mapController = mapView.getController();
		mapController.setZoom(12);
		btnSelectRoute.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				new ChooserDialog(getActivity())
						.withFilter(false, false)
						.withFilter(false, false, "geojson")
						.withStartFile(itl)
						// to handle the result(s)
						.withChosenListener(new ChooserDialog.Result() {
							@Override
							public void onChoosePath(String path, File pathFile) {
								btnSelectRoute.setText(pathFile.getName());
								routeFilePath = path;
								StringBuilder stringBuilder = new StringBuilder();
								InputStream is = null;
								String UTF8 = "utf8";
								int BUFFER_SIZE = 8192;
								try {
									is = new FileInputStream(pathFile);
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
										JSONObject feature = features.getJSONObject(0);
										JSONObject geometry = feature.getJSONObject("geometry");
										JSONArray coordiantes = geometry.getJSONArray("coordinates");
										for(int i = 0; i < coordiantes.length(); i++) {
											JSONArray itemArray = new JSONArray(coordiantes.getString(i));
											Log.v("Lat", String.valueOf(itemArray.getDouble(0)));
											Log.v("Lat", String.valueOf(itemArray.getDouble(1)));
											GeoPoint geoPoint = new GeoPoint(itemArray.getDouble(1), itemArray.getDouble(0));
											pts.add(geoPoint);
											if(i == 0) {
												mapController.setCenter(geoPoint);
												Geoloc firstGeo = new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 5, 10);
												addMarker(firstGeo);
												mapController.setZoom(12);
											}
										}
									}
								} catch (JSONException e) {
									e.printStackTrace();
								}

								drawLine();
								/*try {
									CSVReader reader = new CSVReader(new FileReader(path));
									String[] nextLine;
									int count = 0;
									while ((nextLine = reader.readNext()) != null) {
										GeoPoint geoPoint = new GeoPoint(Float.valueOf(nextLine[0]), Float.valueOf(nextLine[1]));
										pts.add(geoPoint);
										if(count == 0) {
											mapController.setCenter(geoPoint);
											Geoloc geo = new Geoloc(Float.valueOf(nextLine[0]), Float.valueOf(nextLine[1]), 10, 1);
											addMarker(geo);
											mapController.setZoom(18);
										}
										count++;
									}
								} catch (FileNotFoundException e) {
									e.printStackTrace();
								} catch (IOException e) {
									e.printStackTrace();
								}

								drawLine();*/
							}
						})
						.build()
						.show();
			}
		});

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
		//mapView.getOverlays().clear();
		//mapView.getOverlays().add(marker);
		mapController.setCenter(geoPoint);
		path.addPoint(geoPoint);

		//mapController.setZoom(12);
	}

	private void drawLine() {
		Polyline line = new Polyline(mapView);
		line.setTitle("Central Park, NYC");
		line.setSubDescription(Polyline.class.getCanonicalName());
		line.setWidth(6f);
		line.setColor(getResources().getColor(R.color.wallet_holo_blue_light));
		line.setPoints(pts);
		line.setGeodesic(true);
		mapView.getOverlayManager().add(line);
	}

	protected void receiveLocation(Geoloc g) {
		addMarker(g);
	}

	protected View.OnClickListener getStartMockGpsListener() {
		return new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if(!routeFilePath.isEmpty()) {
					Intent intent = new Intent(getActivity(), MockLocationProvider.class);
					intent.putExtra("file_name", routeFilePath);
					getActivity().startService(intent);
				}
			}
		};
	}
}

package me.hoen.android_mock_gps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
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

	private Spinner speedSpinner;

	private ImageButton btnPlay;
	private ImageButton btnStop;
	private ImageButton btnRewind;

	private boolean isPlaying = false;
	private boolean isPause = false;

	private TextView tvLatitude;
	private TextView tvLongitude;
	private TextView tvDegree;

	private double currentLat = 0.0f;
	private double currentLong = 0.0f;
	private double currentDegree = 0.0f;
	private String currentDegreeText = "N";

	private Button btnAdd;
	private Button btnSave;
	private Button btnDelete;

	private Spinner cameraSpinner;

	private Button btnOpenCamera;

	int currentCameraIndex = 0;

	private ArrayList<CameraLocation> cameraLocations = new ArrayList<>();

	private boolean isDeleting = false;

	private Marker currentDeletingCameraLocation;

	private String cameraFileName = "";

	public static final String LOCATION_RECEIVED = "me.hoen.android_mock_gps.LOCATION_RECEIVED";
	protected BroadcastReceiver locationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(LOCATION_RECEIVED)) {
				Geoloc g = (Geoloc) intent.getSerializableExtra("geoloc");
				float degree = intent.getFloatExtra("degree", 0);
				receiveLocation(g, degree);
			}
		}
	};

	private List<GeoPoint> pts = new ArrayList<>();

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_mock_gps, container,
				false);
		btnSelectRoute = rootView.findViewById(R.id.btn_open_route);
		speedSpinner = rootView.findViewById(R.id.speed_spinner);
		cameraSpinner = rootView.findViewById(R.id.camera_speed_spinner);
		btnOpenCamera = rootView.findViewById(R.id.btn_open_camera);

		btnPlay = rootView.findViewById(R.id.start_mock_gps);
		btnStop = rootView.findViewById(R.id.stop_mock_gps);
		btnRewind = rootView.findViewById(R.id.rewind_mock_gps);

		btnAdd = rootView.findViewById(R.id.btn_add);
		btnSave = rootView.findViewById(R.id.btn_save);
		btnDelete = rootView.findViewById(R.id.btn_delete);

		btnAdd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				addCameraLocation();
			}
		});

		btnSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				saveFile();
			}
		});

		btnDelete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				deleteCameraLocation();
			}
		});

		tvLatitude = rootView.findViewById(R.id.tv_latitude);
		tvLongitude = rootView.findViewById(R.id.tv_longitude);
		tvDegree = rootView.findViewById(R.id.tv_degree);

		btnPlay.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if(!isPlaying) {
					if(!isPause) {
						startMockLocation();
						btnPlay.setImageResource(R.drawable.ic_pause);
						isPlaying = true;
					}
				} else {
					if(!isPause) {
						pauseMockLocation();
						btnPlay.setImageResource(R.drawable.ic_play);
					} else {
						resumeMockLocation();
						btnPlay.setImageResource(R.drawable.ic_pause);
					}
				}
			}
		});

		btnStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				stopMockLocation();
				btnPlay.setImageResource(R.drawable.ic_play);
			}
		});

		btnRewind.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				rewindLocation();
			}
		});

		File filePath = getActivity().getFilesDir();
		File osmdroidBasePath = new File(filePath, "osmdroid");
		osmdroidBasePath.mkdirs();
		File osmdroidTilePath = new File(osmdroidBasePath, "tiles");
		osmdroidTilePath.mkdirs();
		Configuration.getInstance().setOsmdroidBasePath(osmdroidBasePath);
		Configuration.getInstance().setOsmdroidTileCache(osmdroidTilePath);

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

		btnOpenCamera.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent mRequestFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
				mRequestFileIntent.setType("*/*");
				startActivityForResult(mRequestFileIntent, 0);
				/*new ChooserDialog(getActivity())
						.withFilter(false, false)
						.withFilter(false, false, "geojson")
						.withStartFile(itl)
						// to handle the result(s)
						.withChosenListener(new ChooserDialog.Result() {
							@Override
							public void onChoosePath(String path, File pathFile) {
								btnOpenCamera.setText(pathFile.getName());
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
										for(int i = 0; i < features.length(); i++) {
											JSONObject feature = features.getJSONObject(i);
											JSONObject geometry = feature.getJSONObject("geometry");
											JSONArray coordiantes = geometry.getJSONArray("coordinates");
											JSONObject properties = feature.getJSONObject("properties");
											String angle = properties.getString("Angle");
											String cameraId = properties.getString("Camera_ID");
											int speed = properties.getInt("Speed");
											JSONArray itemArray = new JSONArray(coordiantes.getString(0));
											GeoPoint geoPoint = new GeoPoint(itemArray.getDouble(1), itemArray.getDouble(0));
											CameraLocation cameraLocation = new CameraLocation(angle, speed, itemArray.getDouble(1), itemArray.getDouble(0), cameraId);
											cameraLocations.add(cameraLocation);
										}

										addCameraPoints();
									}
								} catch (JSONException e) {
									e.printStackTrace();
								}
							}
						})
						.build()
						.show();*/
			}
		});

		btnSelectRoute.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent mRequestFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
				mRequestFileIntent.setType("*/*");
				startActivityForResult(mRequestFileIntent, 1);
				/*new ChooserDialog(getActivity())
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
												addStartLocation(firstGeo, true);
												mapController.setZoom(15);
											} else if (i == coordiantes.length() - 1) {
												Geoloc lastGeo = new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 5, 10);
												addStartLocation(lastGeo, false);
											}
										}
									}
								} catch (JSONException e) {
									e.printStackTrace();
								}

								drawLine();
							}
						})
						.build()
						.show();*/
			}
		});

		return rootView;
	}

	private void openCamera(Uri returnUri, File pathFile) {
		String fileName = DocumentsContract.getDocumentId(returnUri).split(":")[1];
		cameraFileName = fileName;
		btnOpenCamera.setText(pathFile.getName());
		//btnOpenCamera.setText(fileName);
		StringBuilder stringBuilder = new StringBuilder();
		InputStream is = null;
		String UTF8 = "utf8";
		int BUFFER_SIZE = 8192;
		try {
			is = getActivity().getContentResolver().openInputStream(returnUri);
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
				for(int i = 0; i < features.length(); i++) {
					JSONObject feature = features.getJSONObject(i);
					JSONObject geometry = feature.getJSONObject("geometry");
					JSONArray coordiantes = geometry.getJSONArray("coordinates");
					JSONObject properties = feature.getJSONObject("properties");
					double degree = properties.getDouble("Angle");
					//String angle = properties.getString("Angle");
					String cameraId = properties.getString("Camera_ID");
					int speed = properties.getInt("Speed");
					JSONArray itemArray = new JSONArray(coordiantes.getString(0));
					GeoPoint geoPoint = new GeoPoint(itemArray.getDouble(1), itemArray.getDouble(0));
					CameraLocation cameraLocation = new CameraLocation(degree, speed, itemArray.getDouble(1), itemArray.getDouble(0), cameraId);
					cameraLocations.add(cameraLocation);
				}

				addCameraPoints();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void openRoute(Uri returnUri, File pathFile) {
		btnSelectRoute.setText(pathFile.getName());
		routeFilePath = returnUri.toString();
		StringBuilder stringBuilder = new StringBuilder();
		InputStream is = null;
		String UTF8 = "utf8";
		int BUFFER_SIZE = 8192;
		try {
			is = getActivity().getContentResolver().openInputStream(returnUri);
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
						addStartLocation(firstGeo, true);
						mapController.setZoom(15);
					} else if (i == coordiantes.length() - 1) {
						Geoloc lastGeo = new Geoloc(itemArray.getDouble(1), itemArray.getDouble(0), 5, 10);
						addStartLocation(lastGeo, false);
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		drawLine();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode,
								 Intent returnIntent) {
		// If the selection didn't work

		ParcelFileDescriptor mInputPFD;
		if (resultCode != RESULT_OK) {
			// Exit without doing anything else
			return;
		} else {
			// Get the file's content URI from the incoming Intent
			Uri returnUri = returnIntent.getData();
			File file = new File(returnUri.getPath());
			if(requestCode == 0) {
				openCamera(returnUri, file);
			} else if(requestCode == 1) {
				openRoute(returnUri, file);
			}

		}
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

	protected void addMarker(Geoloc g, float degree) {
		GeoPoint geoPoint = new GeoPoint(g.getLatitude(), g.getLongitude());

		Marker marker = new Marker(mapView);
		marker.setPosition(geoPoint);
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		marker.setRelatedObject(g);
		//mapController.setCenter(geoPoint);
		path.addPoint(geoPoint);

		if(!isDeleting) {
			tvLatitude.setText(String.format("%.6f", geoPoint.getLatitude()));
			tvLongitude.setText(String.format("%.6f", geoPoint.getLongitude()));
			currentLat = geoPoint.getLatitude();
			currentLong = geoPoint.getLongitude();
			currentDegree = degree;
			if(degree >= -180 && degree < -135) {
				currentDegreeText = "S";
			}
			if(degree >= -135 && degree < -90) {
				currentDegreeText = "SW";
			}
			if(degree >= -90 && degree < -45) {
				currentDegreeText = "NW";
			}
			if(degree >= -45 && degree < 0) {
				currentDegreeText = "N";
			}
			if(degree >= 0 && degree < 45) {
				currentDegreeText = "NE";
			}
			if(degree >= 45 && degree < 90) {
				currentDegreeText = "E";
			}
			if(degree >= 90 && degree < 135) {
				currentDegreeText = "SE";
			}
			if(degree >= 135 && degree < 180) {
				currentDegreeText = "S";
			}
			tvDegree.setText(String.format("%.4f", degree));
		}
		//mapController.setZoom(12);
	}

	private void addCameraLocation() {
		GeoPoint geoPoint = new GeoPoint(currentLat, currentLong);
		int currentSpeed = (cameraSpinner.getSelectedItemPosition() + 3) * 10;
		CameraLocation cameraLocation = new CameraLocation(currentDegree, currentSpeed, currentLat, currentLong, "Camera_" + (currentCameraIndex + 1));
		cameraLocations.add(cameraLocation);

		Marker marker = new Marker(mapView);
		marker.setPosition(geoPoint);
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		marker.setId(cameraLocation.getCameraID());
		marker.setIcon(getResources().getDrawable(R.drawable.ic_camera));
		mapView.getOverlays().add(marker);
		//mapController.setCenter(geoPoint);
		path.addPoint(geoPoint);

		marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
			@Override
			public boolean onMarkerClick(Marker marker, MapView mapView) {
				Log.v("Delete ID", cameraLocation.getCameraID());
				currentDeletingCameraLocation = marker;
				isDeleting = true;

				btnAdd.setVisibility(View.GONE);
				btnSave.setVisibility(View.GONE);
				btnDelete.setVisibility(View.VISIBLE);

				tvLatitude.setText(String.format("%.6f", cameraLocation.getLatitude()));
				tvLongitude.setText(String.format("%.6f", cameraLocation.getLongitude()));

				return false;
			}
		});

		currentCameraIndex++;
	}

	private void addCameraPoints() {
		for(int i = 0; i < cameraLocations.size(); i++) {
			CameraLocation cameraLocation = cameraLocations.get(i);
			GeoPoint geoPoint = new GeoPoint(cameraLocation.getLatitude(), cameraLocation.getLongitude());

			Marker marker = new Marker(mapView);
			marker.setPosition(geoPoint);
			marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
			marker.setId(cameraLocation.getCameraID());
			marker.setIcon(getResources().getDrawable(R.drawable.ic_camera));
			mapView.getOverlays().add(marker);
			path.addPoint(geoPoint);

			marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
				@Override
				public boolean onMarkerClick(Marker marker, MapView mapView) {
					Log.v("Delete ID", cameraLocation.getCameraID());
					currentDeletingCameraLocation = marker;
					isDeleting = true;

					btnAdd.setVisibility(View.GONE);
					btnSave.setVisibility(View.GONE);
					btnDelete.setVisibility(View.VISIBLE);

					tvLatitude.setText(String.format("%.6f", cameraLocation.getLatitude()));
					tvLongitude.setText(String.format("%.6f", cameraLocation.getLongitude()));

					return false;
				}
			});
		}


		currentCameraIndex = cameraLocations.size();
	}

	private void addStartLocation(Geoloc g, boolean isStart) {
		GeoPoint geoPoint = new GeoPoint(g.getLatitude(), g.getLongitude());

		Marker marker = new Marker(mapView);
		marker.setPosition(geoPoint);
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		marker.setRelatedObject(g);
		//mapView.getOverlays().clear();
		if(isStart) {
			marker.setIcon(getResources().getDrawable(R.drawable.ic_start_location));
			mapController.setCenter(geoPoint);
		} else {
			marker.setIcon(getResources().getDrawable(R.drawable.ic_end_location));
		}
		mapView.getOverlays().add(marker);
		path.addPoint(geoPoint);
	}

	private void saveFile() {
		Log.v("Camera Locations", String.valueOf(cameraLocations.size()));
		//Making Camera Geojson object string
		JSONObject geoJson = new JSONObject();
		try {
			geoJson.put("type", "FeatureCollection");
			geoJson.put("name", "camera_data");
			JSONObject crsJson = new JSONObject();
			crsJson.put("type", "name");
			JSONObject crsPropertyJson = new JSONObject();
			crsPropertyJson.put("name", "urn:ogc:def:crs:OGC:1.3:CRS84");
			crsJson.put("properties", crsPropertyJson);
			geoJson.put("crs", crsJson);
			JSONArray featuresArray = new JSONArray();
			for(int i = 0; i < cameraLocations.size(); i++) {
				CameraLocation item = cameraLocations.get(i);
				JSONObject featureObject = new JSONObject();
				featureObject.put("type", "Feature");
				JSONObject featureProperty = new JSONObject();
				featureProperty.put("Camera_ID", "Camera_" + (i + 1));
				featureProperty.put("Angle", item.getDegree());
				featureProperty.put("Speed", item.getSpeed());
				featureObject.put("properties", featureProperty);

				JSONArray coordinatesArray = new JSONArray();
				JSONArray coordinate = new JSONArray();
				coordinate.put(item.getLongitude());
				coordinate.put(item.getLatitude());
				coordinatesArray.put(coordinate);
				JSONObject geometryJson = new JSONObject();
				geometryJson.put("type", "Point");
				geometryJson.put("coordinates", coordinatesArray);
				featureObject.put("geometry", geometryJson);
				featuresArray.put(featureObject);
			}
			geoJson.put("features", featuresArray);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		Uri uri = Uri.parse(routeFilePath);
		Log.v("Path", uri.getPath());
		String fileName = uri.getPath().split(":")[1];//DocumentsContract.getDocumentId(uri).split(":")[1];
		Log.v("FileName", fileName);
		String[] segments = fileName.split("/");
		String folderName = segments[segments.length - 1].split("_")[0];

		String folderPath = "";
		for(int i = 0; i < segments.length - 1; i++) {
			folderPath = folderPath + segments[i] + "/";
		}

		Log.v("Folder Path", folderPath);

		Log.v("Folder Name", folderName);

		fileName = folderPath + folderName + /*fileName.substring(0, fileName.length() - 8) + */"_cam.geojson";

		if(!cameraFileName.isEmpty()) {
			fileName = cameraFileName;
		}
		Log.v("File Name", fileName);
		File file = new File(FileUtil.getStoragePath(getActivity(), false), fileName);
		try {
			FileWriter out = new FileWriter(file);
			out.write(geoJson.toString());
			out.close();
		}
		catch (IOException e) {
			Log.e("Exception", "File write failed: " + e.toString());
		}

		Toast.makeText(getActivity(), "Successfully saved", Toast.LENGTH_SHORT).show();
	}

	private void deleteCameraLocation() {
		currentDeletingCameraLocation.remove(mapView);
		isDeleting = false;
		btnDelete.setVisibility(View.GONE);
		btnAdd.setVisibility(View.VISIBLE);
		btnSave.setVisibility(View.VISIBLE);
		for(int i = 0; i < cameraLocations.size(); i++) {
			if(cameraLocations.get(i).getCameraID().equals(currentDeletingCameraLocation.getId())) {
				cameraLocations.remove(i);
				return;
			}
		}
	}

	private void drawLine() {
		Polyline line = new Polyline(mapView);
		//line.setSubDescription(Polyline.class.getCanonicalName());
		line.setWidth(10f);
		line.setColor(getResources().getColor(R.color.wallet_holo_blue_light));
		line.setPoints(pts);
		line.setGeodesic(true);
		line.setOnClickListener(new Polyline.OnClickListener() {
			@Override
			public boolean onClick(Polyline polyline, MapView mapView, GeoPoint eventPos) {
				return false;
			}
		});
		mapView.getOverlayManager().add(line);
	}

	protected void receiveLocation(Geoloc g, float degree) {
		addMarker(g, degree);
	}

	private void startMockLocation() {
		if(!routeFilePath.isEmpty()) {
			int position = speedSpinner.getSelectedItemPosition();
			int speed = 40;
			switch(position) {
				case 0:
					speed = 40;
					break;
				case 1:
					speed = 60;
					break;
				case 2:
					speed = 100;
					break;
				case 3:
					speed = 200;
					break;
				case 4:
					speed = 400;
					break;
			}
			Intent intent = new Intent(getActivity(), MockLocationProvider.class);
			intent.putExtra("file_name", routeFilePath);
			intent.putExtra("speed", speed);
			getActivity().startService(intent);
		}
	}

	private void pauseMockLocation() {
		Intent i = new Intent(MockLocationProvider.SERVICE_PAUSE);
		getActivity().sendBroadcast(i);
		isPause = true;
	}

	private void resumeMockLocation() {
		Intent i = new Intent(MockLocationProvider.SERVICE_PLAY);
		getActivity().sendBroadcast(i);
		isPause = false;
	}

	private void stopMockLocation() {
		Intent i = new Intent(MockLocationProvider.SERVICE_STOP);
		getActivity().sendBroadcast(i);
		isPlaying = false;
		isPause = false;
	}

	private void rewindLocation() {
		Intent i = new Intent(MockLocationProvider.SERVICE_REWIND);
		getActivity().sendBroadcast(i);
	}
}

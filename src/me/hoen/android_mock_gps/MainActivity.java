package me.hoen.android_mock_gps;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class MainActivity extends AppCompatActivity {
	public static final String TAG = "me.example";
	private static final int REQUEST_LOCATION = 100;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			ActivityCompat.requestPermissions(this,

					new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
							Manifest.permission.ACCESS_COARSE_LOCATION,
							Manifest.permission.WRITE_EXTERNAL_STORAGE,
							Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_LOCATION);
			return;
		}
		initFragment();
	}

	private void initFragment() {
		Fragment f = new MockGpsFragment();
		FragmentManager fragmentManager = getSupportFragmentManager();
		fragmentManager.beginTransaction().add(android.R.id.content, f, "home")
				.commit();
		fragmentManager.executePendingTransactions();

		Bundle extras = getIntent().getExtras();
		if (extras != null && extras.containsKey("performAction")
				&& extras.getString("performAction").equals("stopMockGps")) {
			Intent i = new Intent(MockLocationProvider.SERVICE_STOP);
			sendBroadcast(i);
			NotificationManager notificationManager = (NotificationManager) this
					.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(MockLocationProvider.NOTIFICATION_ID);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if(requestCode == REQUEST_LOCATION) {
			if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				initFragment();
			}
		}
	}
}

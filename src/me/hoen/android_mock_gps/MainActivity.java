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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (ContextCompat.checkSelfPermission(MainActivity.this,
				Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(MainActivity.this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
				ActivityCompat.requestPermissions(MainActivity.this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
		}
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
}

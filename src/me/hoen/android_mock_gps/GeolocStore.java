package me.hoen.android_mock_gps;

import java.util.ArrayList;

public class GeolocStore {
	protected ArrayList<Geoloc> data = new ArrayList<Geoloc>();
	
	static public GeolocStore instance = new GeolocStore();
	
	static public GeolocStore getInstance(){
		return instance;
	}
	
	private GeolocStore(){
		data.add(new Geoloc(37.4365075, 127.008973, 10, 5));
		data.add(new Geoloc(37.4365126, 127.0089635, 10, 6));
		data.add(new Geoloc(37.4365191, 127.0089555, 10, 7));
		data.add(new Geoloc(37.4365263, 127.0089481, 10, 8));
		data.add(new Geoloc(37.436535, 127.0089404, 10, 9));
		data.add(new Geoloc(37.4365425, 127.0089342, 10, 10));
		data.add(new Geoloc(37.4365506, 127.0089264, 10, 4));
		data.add(new Geoloc(37.4365572, 127.0089168, 10, 3));
		data.add(new Geoloc(37.4365631, 127.0089065, 10, 2));
		data.add(new Geoloc(37.4365684, 127.0088957, 10, 1));
	}
	
	public ArrayList<Geoloc> getGeolocs(){
		return data;
	}
}

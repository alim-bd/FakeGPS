package me.hoen.android_mock_gps;

public class CameraLocation {
    private String angle;
    private double degree;
    private int speed;
    private double latitude;
    private double longitude;
    private String cameraID;

    public CameraLocation(double degree, int speed, double latitude, double longitude, String cameraID) {
        this.degree = degree;
        this.speed = speed;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cameraID = cameraID;
    }

    public double getDegree() {
        return degree;
    }

    public String getAngle() {
        return angle;
    }

    public int getSpeed() {
        return speed;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCameraID() {
        return cameraID;
    }
}

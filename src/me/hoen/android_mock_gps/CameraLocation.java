package me.hoen.android_mock_gps;

public class CameraLocation {
    private String angle;
    private int speed;
    private double latitude;
    private double longitude;
    private String cameraID;

    public CameraLocation(String angle, int speed, double latitude, double longitude, String cameraID) {
        this.angle = angle;
        this.speed = speed;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cameraID = cameraID;
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

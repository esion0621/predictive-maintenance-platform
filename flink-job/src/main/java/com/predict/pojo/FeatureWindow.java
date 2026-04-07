package com.predict.pojo;

import java.io.Serializable;

public class FeatureWindow implements Serializable {
    private String deviceId;
    private long windowEndTime;
    private double avgTemperature;
    private double maxVibration;
    private double currentVariance;
    private double pressureChangeRate;

    public FeatureWindow() {}

    public FeatureWindow(String deviceId, long windowEndTime, double avgTemperature, double maxVibration, double currentVariance, double pressureChangeRate) {
        this.deviceId = deviceId;
        this.windowEndTime = windowEndTime;
        this.avgTemperature = avgTemperature;
        this.maxVibration = maxVibration;
        this.currentVariance = currentVariance;
        this.pressureChangeRate = pressureChangeRate;
    }

    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public long getWindowEndTime() { return windowEndTime; }
    public void setWindowEndTime(long windowEndTime) { this.windowEndTime = windowEndTime; }
    public double getAvgTemperature() { return avgTemperature; }
    public void setAvgTemperature(double avgTemperature) { this.avgTemperature = avgTemperature; }
    public double getMaxVibration() { return maxVibration; }
    public void setMaxVibration(double maxVibration) { this.maxVibration = maxVibration; }
    public double getCurrentVariance() { return currentVariance; }
    public void setCurrentVariance(double currentVariance) { this.currentVariance = currentVariance; }
    public double getPressureChangeRate() { return pressureChangeRate; }
    public void setPressureChangeRate(double pressureChangeRate) { this.pressureChangeRate = pressureChangeRate; }

    public double[] getFeatures() {
        return new double[]{avgTemperature, maxVibration, currentVariance, pressureChangeRate};
    }
}

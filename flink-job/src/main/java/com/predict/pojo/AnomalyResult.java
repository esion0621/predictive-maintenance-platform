package com.predict.pojo;

import java.io.Serializable;

public class AnomalyResult implements Serializable {
    private String deviceId;
    private long timestamp;
    private double[] features;
    private double anomalyScore;
    private boolean isAlarm;

    public AnomalyResult() {}

    public AnomalyResult(String deviceId, long timestamp, double[] features, double anomalyScore, boolean isAlarm) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.features = features;
        this.anomalyScore = anomalyScore;
        this.isAlarm = isAlarm;
    }

    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public double[] getFeatures() { return features; }
    public void setFeatures(double[] features) { this.features = features; }
    public double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }
    public boolean isAlarm() { return isAlarm; }
    public void setAlarm(boolean alarm) { isAlarm = alarm; }
}

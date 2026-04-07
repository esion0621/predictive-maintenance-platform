package com.predict.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class SensorData implements Serializable {
    @JsonProperty("device_id")
    private String deviceId;
    private long timestamp;
    private double temperature;
    private double vibration;
    private double current;
    private double pressure;

    public SensorData() {}

    public SensorData(String deviceId, long timestamp, double temperature, double vibration, double current, double pressure) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.vibration = vibration;
        this.current = current;
        this.pressure = pressure;
    }

    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getVibration() { return vibration; }
    public void setVibration(double vibration) { this.vibration = vibration; }
    public double getCurrent() { return current; }
    public void setCurrent(double current) { this.current = current; }
    public double getPressure() { return pressure; }
    public void setPressure(double pressure) { this.pressure = pressure; }

    @Override
    public String toString() {
        return "SensorData{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp +
                ", temperature=" + temperature +
                ", vibration=" + vibration +
                ", current=" + current +
                ", pressure=" + pressure +
                '}';
    }
}

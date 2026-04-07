package com.predict.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predict.config.JobConfig;
import com.predict.pojo.AnomalyResult;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class MySQLSink extends RichSinkFunction<AnomalyResult> {
    private transient Connection connection;
    private transient PreparedStatement preparedStatement;
    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        objectMapper = new ObjectMapper();
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(JobConfig.MYSQL_URL, JobConfig.MYSQL_USER, JobConfig.MYSQL_PASSWORD);
        String sql = "INSERT INTO alarm_event (device_id, alarm_time, anomaly_score, feature_values) VALUES (?, ?, ?, ?)";
        preparedStatement = connection.prepareStatement(sql);
    }

    @Override
    public void invoke(AnomalyResult value, Context context) throws Exception {
        if (!value.isAlarm()) {
            return;
        }
        Map<String, Double> featuresMap = new HashMap<>();
        featuresMap.put("avg_temperature", value.getFeatures()[0]);
        featuresMap.put("max_vibration", value.getFeatures()[1]);
        featuresMap.put("current_variance", value.getFeatures()[2]);
        featuresMap.put("pressure_change_rate", value.getFeatures()[3]);
        String featuresJson = objectMapper.writeValueAsString(featuresMap);

        preparedStatement.setString(1, value.getDeviceId());
        preparedStatement.setTimestamp(2, new Timestamp(value.getTimestamp()));
        preparedStatement.setDouble(3, value.getAnomalyScore());
        preparedStatement.setString(4, featuresJson);
        preparedStatement.executeUpdate();
    }

    @Override
    public void close() throws Exception {
        if (preparedStatement != null) preparedStatement.close();
        if (connection != null) connection.close();
    }
}

package com.predict.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predict.config.JobConfig;
import com.predict.pojo.AnomalyResult;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class MySQLSink extends RichSinkFunction<AnomalyResult> {
    private static final String INSERT_SQL =
            "INSERT INTO alarm_event (device_id, alarm_time, anomaly_score, feature_values, source, rule_id) VALUES (?, ?, ?, ?, ?, ?)";

    private transient Connection connection;
    private transient PreparedStatement preparedStatement;
    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        objectMapper = new ObjectMapper();
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(JobConfig.MYSQL_URL, JobConfig.MYSQL_USER, JobConfig.MYSQL_PASSWORD);
        preparedStatement = connection.prepareStatement(INSERT_SQL);
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || !connection.isValid(5)) {
            if (preparedStatement != null) {
                try { preparedStatement.close(); } catch (Exception ignored) {}
            }
            if (connection != null) {
                try { connection.close(); } catch (Exception ignored) {}
            }
            connection = DriverManager.getConnection(JobConfig.MYSQL_URL, JobConfig.MYSQL_USER, JobConfig.MYSQL_PASSWORD);
            preparedStatement = connection.prepareStatement(INSERT_SQL);
        }
    }

    @Override
    public void invoke(AnomalyResult value, Context context) throws Exception {
        if (!value.isAlarm()) {
            return;
        }
        ensureConnection();

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
        preparedStatement.setString(5, value.getSource() != null ? value.getSource() : "MODEL");
        preparedStatement.setString(6, value.getRuleId());
        preparedStatement.executeUpdate();
    }

    @Override
    public void close() throws Exception {
        if (preparedStatement != null) {
            try { preparedStatement.close(); } catch (Exception e) { /* ignore */ }
        }
        if (connection != null) {
            try { connection.close(); } catch (Exception e) { /* ignore */ }
        }
    }
}


package com.predict.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predict.config.JobConfig;
import com.predict.pojo.AnomalyResult;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisSink extends RichSinkFunction<AnomalyResult> {
    private static final Logger LOG = LoggerFactory.getLogger(RedisSink.class);
    private transient JedisPool jedisPool;
    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        objectMapper = new ObjectMapper();
        poolConfig.setMaxTotal(10);
        jedisPool = new JedisPool(poolConfig, JobConfig.REDIS_HOST, JobConfig.REDIS_PORT, 2000, JobConfig.REDIS_PASSWORD);
    }

    @Override
    public void invoke(AnomalyResult value, Context context) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String latestKey = "device:latest:" + value.getDeviceId();

            // CEP 优先策略：如果当前 Redis 中 source=CEP 且时间差 < 10s，MODEL 结果不覆盖
            String currentSource = jedis.hget(latestKey, "source");
            String updateTimeStr = jedis.hget(latestKey, "update_time");
            if ("MODEL".equals(value.getSource()) && "CEP".equals(currentSource) && updateTimeStr != null) {
                try {
                    long lastUpdate = Long.parseLong(updateTimeStr);
                    if (System.currentTimeMillis() - lastUpdate < 10000) {
                        return; // CEP 告警 10s 内，不覆盖
                    }
                } catch (NumberFormatException ignored) {}
            }

            // 更新设备最新状态
            jedis.hset(latestKey, "temperature", String.valueOf(value.getFeatures()[0]));
            jedis.hset(latestKey, "vibration", String.valueOf(value.getFeatures()[1]));
            jedis.hset(latestKey, "current", String.valueOf(value.getFeatures()[2]));
            jedis.hset(latestKey, "pressure", String.valueOf(value.getFeatures()[3]));
            jedis.hset(latestKey, "anomaly_score", String.valueOf(value.getAnomalyScore()));
            jedis.hset(latestKey, "update_time", String.valueOf(System.currentTimeMillis()));
            jedis.hset(latestKey, "source", value.getSource() != null ? value.getSource() : "MODEL");
            jedis.hset(latestKey, "rule_id", value.getRuleId() != null ? value.getRuleId() : "");

            // 如果告警，加入告警列表
            if (value.isAlarm()) {
                String alarmJson = objectMapper.writeValueAsString(value);
                jedis.lpush("alarm:list", alarmJson);
                jedis.ltrim("alarm:list", 0, 99); // 保留最近100条
            }
        } catch (Exception e) {
            LOG.error("Redis write failed for device: {}", value.getDeviceId(), e);
        }
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}


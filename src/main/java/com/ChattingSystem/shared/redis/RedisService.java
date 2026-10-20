
package com.ChattingSystem.shared.redis;

import tools.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
public class RedisService {
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public RedisService(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public <T> void set(String key, T value, Duration ttl) {
        if(value == null){
            return;
        }
        String json = jsonMapper.writeValueAsString(value);
        redisTemplate.opsForValue().set(key,json,ttl);
    }

    public <T> Optional<T> get(String key, Class<T> clazz){
        String json = redisTemplate.opsForValue().get(key);
        if(json == null || json.isBlank()){
            return Optional.empty();
        }
        return Optional.of(jsonMapper.readValue(json, clazz));
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeReference){
        String json = redisTemplate.opsForValue().get(key);
        if(json == null || json.isBlank()){
            return Optional.empty();
        }
        return Optional.of(jsonMapper.readValue(json, typeReference));
    }

    public <T> T getOrLoad(String key, Class<T> clazz, Duration ttl, Supplier<T> dbLoader){
        try{
            Optional<T> optional = get(key, clazz);
            if(optional.isPresent()){
                return optional.get();
            }

        }catch (Exception e){
            log.warn("Không thể đọc cache từ Redis cho key [{}], chuyển sang lấy từ DB", key, e);
        }
        T dataFromDb = dbLoader.get();
        if(dataFromDb != null){
            try{
                set(key, dataFromDb, ttl);
            }catch (Exception e){
                log.warn("Không thể lưu cache xuống Redis cho key [{}]", key, e);
            }
        }
        return dataFromDb;
    }

    public void delete(String key){
        redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
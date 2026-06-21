package com.example.dacn2_beserver.repository;

import com.example.dacn2_beserver.model.device.Device;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends MongoRepository<Device, String> {
    Optional<Device> findByUserIdAndDeviceId(String userId, String deviceId);

    List<Device> findAllByUserId(String userId);

    void deleteAllByUserId(String userId);
}
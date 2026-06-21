package com.restaurant.pos.client.controller;

import com.restaurant.pos.client.domain.Device;
import com.restaurant.pos.client.service.DeviceService;
import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Device>>> getDevices() {
        return ResponseEntity.ok(ApiResponse.success(service.getMyDevices()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Device>> getDevice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getDeviceById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Device>> createDevice(@RequestBody Device device) {
        return ResponseEntity.ok(ApiResponse.success(service.saveDevice(device)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Device>> updateDevice(@PathVariable UUID id, @RequestBody Device device) {
        device.setId(id);
        return ResponseEntity.ok(ApiResponse.success(service.saveDevice(device)));
    }

}

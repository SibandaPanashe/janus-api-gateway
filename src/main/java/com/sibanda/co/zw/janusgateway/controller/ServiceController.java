package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.entity.ServiceEntity;
import com.sibanda.co.zw.janusgateway.repository.ServiceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/admin/services")
@CrossOrigin // Allow frontend
public class ServiceController {

    private final ServiceRepository serviceRepo;

    public ServiceController(ServiceRepository serviceRepo) {
        this.serviceRepo = serviceRepo;
    }

    @GetMapping
    public ResponseEntity<List<ServiceEntity>> list(@RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(serviceRepo.findByClientId(clientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceEntity> getById(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        Optional<ServiceEntity> service = serviceRepo.findById(id);
        if (service.isPresent() && service.get().getClientId().equals(clientId)) {
            return ResponseEntity.ok(service.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ServiceEntity> create(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestBody Map<String, String> body) {

        // Support both "backendUrl" and "url" depending on what the frontend sends
        String url = body.containsKey("backendUrl") ? body.get("backendUrl") : body.get("url");

        ServiceEntity service = ServiceEntity.builder()
                .clientId(clientId)
                .name(body.getOrDefault("name", "Unnamed Service"))
                .backendUrl(url)
                .build();

        return ResponseEntity.ok(serviceRepo.save(service));
    }
}
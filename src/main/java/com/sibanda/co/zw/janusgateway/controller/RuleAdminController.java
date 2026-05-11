package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.entity.RuleEntity;
import com.sibanda.co.zw.janusgateway.repository.RuleRepository;
import com.sibanda.co.zw.janusgateway.service.DynamicRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/rules")
public class RuleAdminController {

    private final DynamicRuleService dynamicRuleService;
    private final RuleRepository ruleRepository;

    public RuleAdminController(DynamicRuleService dynamicRuleService,
                               RuleRepository ruleRepository) {
        this.dynamicRuleService = dynamicRuleService;
        this.ruleRepository = ruleRepository;
    }

    @GetMapping
    public ResponseEntity<List<RuleEntity>> listRules() {
        return ResponseEntity.ok(ruleRepository.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<RuleEntity>> listActiveRules() {
        return ResponseEntity.ok(ruleRepository.findByActiveTrueOrderByPriorityAsc());
    }

    @PostMapping
    public ResponseEntity<RuleEntity> createRule(@RequestBody Map<String, Object> body) {
        RuleEntity rule = RuleEntity.builder()
                .id(UUID.randomUUID().toString())
                .name((String) body.get("name"))
                .description((String) body.getOrDefault("description", ""))
                .drlContent((String) body.get("drlContent"))
                .priority((Integer) body.getOrDefault("priority", 0))
                .active(true)
                .version(1)
                .build();

        RuleEntity saved = dynamicRuleService.saveRule(rule);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleEntity> updateRule(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ruleRepository.findById(id)
                .map(existing -> {
                    if (body.containsKey("drlContent")) {
                        existing.setDrlContent((String) body.get("drlContent"));
                    }
                    if (body.containsKey("priority")) {
                        existing.setPriority((Integer) body.get("priority"));
                    }
                    if (body.containsKey("name")) {
                        existing.setName((String) body.get("name"));
                    }
                    existing.setVersion(existing.getVersion() + 1);
                    RuleEntity saved = dynamicRuleService.saveRule(existing);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        dynamicRuleService.toggleRule(id, active);
        return ResponseEntity.ok(Map.of("id", id, "active", active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        dynamicRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        dynamicRuleService.reloadRules();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
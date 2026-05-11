package com.sibanda.co.zw.janusgateway.service;

import com.sibanda.co.zw.janusgateway.entity.RuleEntity;
import com.sibanda.co.zw.janusgateway.repository.RuleRepository;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DynamicRuleService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRuleService.class);

    private final RuleRepository ruleRepository;
    private final AtomicReference<KieContainer> kieContainerRef = new AtomicReference<>();

    public DynamicRuleService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * Load all active rules from the database and compile them into a KieContainer.
     * This can be called at any time to hot-reload rules without restarting the application.
     */
    public synchronized KieContainer reloadRules() {
        log.info("[DynamicRules] Reloading rules from database...");

        List<RuleEntity> activeRules = ruleRepository.findByActiveTrueOrderByPriorityAsc();

        if (activeRules.isEmpty()) {
            log.warn("[DynamicRules] No active rules found in database. Keeping existing container.");
            return kieContainerRef.get();
        }

        KieServices kieServices = KieServices.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        for (RuleEntity rule : activeRules) {
            String drlContent = rule.getDrlContent();
            String fileName = "rules/db/" + rule.getName().replaceAll("[^a-zA-Z0-9]", "_") + ".drl";

            log.info("[DynamicRules] Loading rule: {} (priority={}, version={})",
                    rule.getName(), rule.getPriority(), rule.getVersion());

            kieFileSystem.write(
                    org.kie.internal.io.ResourceFactory.newByteArrayResource(
                            drlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    ).setSourcePath(fileName)
            );
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(
                org.kie.api.builder.Message.Level.ERROR)) {
            log.error("[DynamicRules] Build errors:\n{}", kieBuilder.getResults().toString());
            throw new RuntimeException("Rule compilation failed: " + kieBuilder.getResults().toString());
        }

        KieModule kieModule = kieBuilder.getKieModule();
        KieContainer newContainer = kieServices.newKieContainer(kieModule.getReleaseId());
        kieContainerRef.set(newContainer);

        log.info("[DynamicRules] Successfully loaded {} rules into new KieContainer", activeRules.size());
        return newContainer;
    }

    /**
     * Get the current KieContainer. Loads from DB on first call if not already loaded.
     */
    public KieContainer getKieContainer() {
        KieContainer container = kieContainerRef.get();
        if (container == null) {
            synchronized (this) {
                container = kieContainerRef.get();
                if (container == null) {
                    container = reloadRules();
                }
            }
        }
        return container;
    }

    /**
     * Add or update a rule and hot-reload.
     */
    public RuleEntity saveRule(RuleEntity rule) {
        RuleEntity saved = ruleRepository.save(rule);
        log.info("[DynamicRules] Rule '{}' saved. Triggering hot reload...", saved.getName());
        reloadRules();
        return saved;
    }

    /**
     * Toggle a rule active/inactive and hot-reload.
     */
    public void toggleRule(String ruleId, boolean active) {
        ruleRepository.findById(ruleId).ifPresent(rule -> {
            rule.setActive(active);
            ruleRepository.save(rule);
            log.info("[DynamicRules] Rule '{}' set to active={}. Triggering hot reload...", rule.getName(), active);
            reloadRules();
        });
    }

    /**
     * Delete a rule and hot-reload.
     */
    public void deleteRule(String ruleId) {
        ruleRepository.deleteById(ruleId);
        log.info("[DynamicRules] Rule '{}' deleted. Triggering hot reload...", ruleId);
        reloadRules();
    }
}
package com.nexusops.platform.automation;

import com.nexusops.platform.identity.Organization;
import com.nexusops.platform.identity.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "automation.scheduler.enabled", havingValue = "true")
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final OrganizationRepository organizationRepository;
    private final AutomationService automationService;

    public AutomationScheduler(
            OrganizationRepository organizationRepository,
            AutomationService automationService) {
        this.organizationRepository = organizationRepository;
        this.automationService = automationService;
    }

    @Scheduled(cron = "${automation.scheduler.cron:0 0 */6 * * *}")
    public void runPeriodicWorkflows() {
        for (Organization org : organizationRepository.findAll()) {
            try {
                automationService.runScheduledForOrganization(org.getId());
            } catch (RuntimeException ex) {
                log.warn("Scheduled automation sweep failed for organization {}: {}",
                        org.getId(), ex.getMessage());
            }
        }
    }
}

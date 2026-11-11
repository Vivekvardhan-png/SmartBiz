package com.nexusops.platform.automation;

import com.nexusops.platform.crm.Lead;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.crm.LeadStatus;
import com.nexusops.platform.inventory.Product;
import com.nexusops.platform.inventory.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executes a single deterministic workflow inside an isolated transaction so
 * partial alert writes roll back on unexpected errors and failure audit runs
 * can always be persisted cleanly.
 */
@Component
public class AutomationWorkflowExecutor {

    private static final Set<LeadStatus> OPEN_LEAD_STATUSES = EnumSet.of(
            LeadStatus.NEW,
            LeadStatus.CONTACTED,
            LeadStatus.QUALIFIED,
            LeadStatus.PROPOSAL
    );

    private final ProductRepository productRepository;
    private final LeadRepository leadRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationAlertRepository alertRepository;

    public AutomationWorkflowExecutor(
            ProductRepository productRepository,
            LeadRepository leadRepository,
            AutomationRunRepository runRepository,
            AutomationAlertRepository alertRepository) {
        this.productRepository = productRepository;
        this.leadRepository = leadRepository;
        this.runRepository = runRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutomationRun executeWorkflow(
            UUID organizationId,
            AutomationWorkflowType workflowType,
            AutomationTriggerSource triggerSource,
            UUID actorUserId) {

        AutomationRun run = runRepository.save(new AutomationRun(
                organizationId, workflowType, triggerSource, actorUserId));

        int created = 0;
        int skippedDuplicate = 0;

        if (workflowType == AutomationWorkflowType.LOW_STOCK_ALERT) {
            List<Product> lowStockProducts = productRepository.findActiveLowStockProducts(organizationId);
            for (Product product : lowStockProducts) {
                String dedupeKey = "LOW_STOCK:" + product.getId();
                boolean existsOpen = alertRepository.existsByOrganizationIdAndDedupeKeyAndStatus(
                        organizationId, dedupeKey, AutomationAlertStatus.OPEN);
                if (existsOpen) {
                    skippedDuplicate++;
                    continue;
                }
                String title = "Low stock: " + product.getSku() + " (" + product.getName() + ")";
                String message = String.format(
                        "Available stock %s %s is at or below threshold %s %s.",
                        product.getStockQuantity().stripTrailingZeros().toPlainString(),
                        product.getUnitOfMeasure(),
                        product.getLowStockThreshold().stripTrailingZeros().toPlainString(),
                        product.getUnitOfMeasure());
                alertRepository.save(new AutomationAlert(
                        organizationId,
                        workflowType,
                        AutomationEntityType.PRODUCT,
                        product.getId(),
                        dedupeKey,
                        AutomationAlertSeverity.WARNING,
                        truncate(title, 255),
                        truncate(message, 1000),
                        run.getId()
                ));
                created++;
            }
        } else if (workflowType == AutomationWorkflowType.FOLLOW_UP_REMINDER) {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
            List<Lead> dueLeads = leadRepository.findDueFollowUpLeads(
                    organizationId, todayUtc, OPEN_LEAD_STATUSES);
            for (Lead lead : dueLeads) {
                String dedupeKey = "LEAD_FOLLOW_UP:" + lead.getId() + ":" + lead.getFollowUpDate();
                if (alertRepository.existsByOrganizationIdAndDedupeKey(organizationId, dedupeKey)) {
                    skippedDuplicate++;
                    continue;
                }
                String title = "Follow-up due: " + lead.getTitle();
                String message = String.format(
                        "Lead '%s' (%s) has follow-up scheduled on %s (status: %s).",
                        lead.getTitle(),
                        lead.getContactName(),
                        lead.getFollowUpDate(),
                        lead.getStatus().name());
                alertRepository.save(new AutomationAlert(
                        organizationId,
                        workflowType,
                        AutomationEntityType.LEAD,
                        lead.getId(),
                        dedupeKey,
                        AutomationAlertSeverity.INFO,
                        truncate(title, 255),
                        truncate(message, 1000),
                        run.getId()
                ));
                created++;
            }
        }

        run.markCompleted(created, skippedDuplicate);
        return runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutomationRun recordFailedRun(
            UUID organizationId,
            AutomationWorkflowType workflowType,
            AutomationTriggerSource triggerSource,
            UUID actorUserId,
            String errorMessage) {

        AutomationRun failed = new AutomationRun(
                organizationId, workflowType, triggerSource, actorUserId);
        failed.markFailed(errorMessage);
        return runRepository.save(failed);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}

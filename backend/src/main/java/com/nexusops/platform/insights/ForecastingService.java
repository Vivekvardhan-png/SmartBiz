package com.nexusops.platform.insights;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.finance.InvoiceRepository;
import com.nexusops.platform.insights.dto.InsightsDtos.ForecastEvaluationDto;
import com.nexusops.platform.insights.dto.InsightsDtos.ForecastPointDto;
import com.nexusops.platform.insights.dto.InsightsDtos.ForecastResponse;
import com.nexusops.platform.inventory.Product;
import com.nexusops.platform.inventory.ProductRepository;
import com.nexusops.platform.inventory.StockMovement;
import com.nexusops.platform.inventory.StockMovementRepository;
import com.nexusops.platform.security.TenantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Resilient client for the optional Python forecasting microservice.
 * Never fabricates forecasts or fails the rest of the platform when the
 * Python service is disabled or unreachable.
 */
@Service
public class ForecastingService {

    private static final Logger log = LoggerFactory.getLogger(ForecastingService.class);
    private static final int LOOKBACK_DAYS = 90;
    private static final int MIN_DAYS_REQUIRED = 14;

    private final ForecastingProperties properties;
    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final RestClient restClient;

    public ForecastingService(
            ForecastingProperties properties,
            InvoiceRepository invoiceRepository,
            ProductRepository productRepository,
            StockMovementRepository movementRepository) {
        this.properties = properties;
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecastSales(TenantPrincipal principal, int horizonDays) {
        int safeHorizon = validateHorizon(horizonDays);
        String target = "Daily invoiced revenue (USD)";

        if (!properties.isEnabled()) {
            return unavailableResponse(target, safeHorizon,
                    "Forecasting service is disabled (FORECASTING_ENABLED=false). Reporting and operations remain fully available.");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(LOOKBACK_DAYS);

        List<PythonPoint> history = invoiceRepository
                .findDailyRevenueInDateRange(principal.organizationId(), from, today)
                .stream()
                .map(row -> new PythonPoint(row.getIssueDate(), row.getRevenue()))
                .toList();

        PythonSalesRequest payload = new PythonSalesRequest(safeHorizon, history);
        return invokePythonForecast("/internal/v1/forecast/sales", payload, target, safeHorizon);
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecastInventoryDemand(
            TenantPrincipal principal,
            UUID productId,
            int horizonDays) {
        int safeHorizon = validateHorizon(horizonDays);

        Product product = productRepository
                .findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        String target = "Daily unit demand for SKU " + product.getSku();
        if (!properties.isEnabled()) {
            return unavailableResponse(target, safeHorizon,
                    "Forecasting service is disabled (FORECASTING_ENABLED=false). Inventory operations remain fully available.");
        }

        Instant fromInstant = LocalDate.now(ZoneOffset.UTC)
                .minusDays(LOOKBACK_DAYS)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        List<StockMovement> deductions = movementRepository.findOrderDeductionsSince(
                principal.organizationId(), product.getId(), fromInstant);

        Map<LocalDate, BigDecimal> dailyDemand = new TreeMap<>();
        for (StockMovement m : deductions) {
            LocalDate day = m.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal positiveUsage = m.getQuantityDelta().abs();
            dailyDemand.merge(day, positiveUsage, BigDecimal::add);
        }

        List<PythonPoint> history = dailyDemand.entrySet().stream()
                .map(e -> new PythonPoint(e.getKey(), e.getValue()))
                .toList();

        PythonInventoryRequest payload = new PythonInventoryRequest(
                product.getSku(), product.getStockQuantity(), safeHorizon, history);
        return invokePythonForecast("/internal/v1/forecast/inventory-demand", payload, target, safeHorizon);
    }

    private ForecastResponse invokePythonForecast(
            String path,
            Object payload,
            String target,
            int horizonDays) {
        try {
            String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
            PythonForecastResponse raw = restClient.post()
                    .uri(baseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(PythonForecastResponse.class);

            if (raw == null) {
                return unavailableResponse(target, horizonDays,
                        "Forecasting service returned an empty response.");
            }

            ForecastEvaluationDto eval = raw.evaluation() != null
                    ? new ForecastEvaluationDto(
                            raw.evaluation().holdoutDays(),
                            raw.evaluation().baselineMae(),
                            raw.evaluation().baselineWape(),
                            raw.evaluation().modelMae(),
                            raw.evaluation().modelWape(),
                            raw.evaluation().selectedModel())
                    : null;

            List<ForecastPointDto> points = raw.predictions() != null
                    ? raw.predictions().stream()
                            .map(p -> new ForecastPointDto(p.date(), p.predictedValue(), p.baselineValue()))
                            .toList()
                    : List.of();

            return new ForecastResponse(
                    raw.status(),
                    true,
                    raw.predictionTarget(),
                    raw.horizonDays(),
                    raw.actualDays(),
                    raw.nonZeroDays(),
                    raw.minimumDaysRequired(),
                    raw.message(),
                    eval,
                    points,
                    raw.projectedTotal(),
                    raw.estimatedStockoutDate(),
                    raw.limitations() != null ? raw.limitations() : List.of()
            );
        } catch (Exception ex) {
            log.warn("Forecasting service call failed [{}]: {}", path, ex.getMessage());
            return unavailableResponse(target, horizonDays,
                    "Forecasting service is currently unavailable. Business reports and operations continue unaffected.");
        }
    }

    private static int validateHorizon(int horizonDays) {
        if (horizonDays < 7 || horizonDays > 30) {
            throw new BusinessRuleException("Forecast horizonDays must be between 7 and 30.");
        }
        return horizonDays;
    }

    private static ForecastResponse unavailableResponse(String target, int horizonDays, String message) {
        return new ForecastResponse(
                "UNAVAILABLE",
                false,
                target,
                horizonDays,
                0,
                0,
                MIN_DAYS_REQUIRED,
                message,
                null,
                List.of(),
                null,
                null,
                List.of("Forecasting microservice is unreachable or disabled; no predictions were generated.")
        );
    }

    record PythonPoint(LocalDate date, BigDecimal value) {}

    record PythonSalesRequest(
            @JsonProperty("horizon_days") int horizonDays,
            List<PythonPoint> history
    ) {}

    record PythonInventoryRequest(
            String sku,
            @JsonProperty("current_stock") BigDecimal currentStock,
            @JsonProperty("horizon_days") int horizonDays,
            List<PythonPoint> history
    ) {}

    record PythonEvaluation(
            @JsonProperty("holdout_days") int holdoutDays,
            @JsonProperty("baseline_mae") BigDecimal baselineMae,
            @JsonProperty("baseline_wape") BigDecimal baselineWape,
            @JsonProperty("model_mae") BigDecimal modelMae,
            @JsonProperty("model_wape") BigDecimal modelWape,
            @JsonProperty("selected_model") String selectedModel
    ) {}

    record PythonForecastPoint(
            LocalDate date,
            @JsonProperty("predicted_value") BigDecimal predictedValue,
            @JsonProperty("baseline_value") BigDecimal baselineValue
    ) {}

    record PythonForecastResponse(
            String status,
            @JsonProperty("prediction_target") String predictionTarget,
            @JsonProperty("horizon_days") int horizonDays,
            @JsonProperty("actual_days") int actualDays,
            @JsonProperty("non_zero_days") int nonZeroDays,
            @JsonProperty("minimum_days_required") int minimumDaysRequired,
            String message,
            PythonEvaluation evaluation,
            List<PythonForecastPoint> predictions,
            @JsonProperty("projected_total") BigDecimal projectedTotal,
            @JsonProperty("estimated_stockout_date") LocalDate estimatedStockoutDate,
            List<String> limitations
    ) {}
}


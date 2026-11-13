package app.financecopilot.dto;

import app.financecopilot.domain.Frequency;
import app.financecopilot.domain.SubscriptionStatus;
import app.financecopilot.dto.TransactionDtos.CategoryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record SubscriptionResponse(Long id, String merchant, CategoryResponse category, BigDecimal averageAmount,
                                       Frequency frequency, int occurrences, LocalDate lastChargeDate,
                                       LocalDate nextExpectedDate, SubscriptionStatus status, BigDecimal yearlyCost) {
    }
}

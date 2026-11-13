package app.financecopilot.dto;

import app.financecopilot.domain.CategoryKind;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionResponse(Long id, LocalDate date, String description, String merchant, BigDecimal amount,
                                      Direction direction, BigDecimal balance, CategoryResponse category,
                                      CategorySource categorySource) {
    }

    public record CategoryResponse(Long id, String name, CategoryKind kind, String color, boolean system) {
    }

    /** Query parameters for GET /api/transactions; all optional. */
    public record TransactionFilter(LocalDate from, LocalDate to, Long categoryId, Direction direction,
                                    String q, BigDecimal minAmount, BigDecimal maxAmount, CategorySource source) {
    }

    public record RecategorizeRequest(@NotNull Long categoryId, boolean createRule) {
    }

    public record RecategorizeResponse(TransactionResponse transaction, boolean ruleCreated, int similarUpdated) {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 60) String name,
            CategoryKind kind,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #22c55e") String color) {
    }

    /** Stable pagination envelope (Spring's Page JSON is not a guaranteed contract). */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}

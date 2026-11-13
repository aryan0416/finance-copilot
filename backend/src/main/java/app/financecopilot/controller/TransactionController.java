package app.financecopilot.controller;

import app.financecopilot.dto.TransactionDtos.PageResponse;
import app.financecopilot.dto.TransactionDtos.RecategorizeRequest;
import app.financecopilot.dto.TransactionDtos.RecategorizeResponse;
import app.financecopilot.dto.TransactionDtos.TransactionFilter;
import app.financecopilot.dto.TransactionDtos.TransactionResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
@Validated
public class TransactionController {

    /** API sort keys → entity properties; anything else is rejected rather than passed through. */
    private static final Map<String, String> SORTABLE = Map.of("date", "date", "amount", "amount", "merchant", "merchant");

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Paginated, filterable transaction list (filters: from, to, categoryId, direction, q, minAmount, maxAmount, source)")
    public PageResponse<TransactionResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @ModelAttribute TransactionFilter filter,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "25") @Min(1) @Max(200) int size,
                                                  @RequestParam(defaultValue = "date") String sort,
                                                  @RequestParam(defaultValue = "desc") String dir) {
        String property = SORTABLE.getOrDefault(sort, "date");
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id")));
        return transactionService.search(user.id(), filter, pageable);
    }

    @PatchMapping("/{id}/category")
    @Operation(summary = "Recategorise a transaction; optionally create a rule for its merchant")
    public RecategorizeResponse recategorize(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                             @Valid @RequestBody RecategorizeRequest request) {
        return transactionService.recategorize(user.id(), id, request);
    }
}

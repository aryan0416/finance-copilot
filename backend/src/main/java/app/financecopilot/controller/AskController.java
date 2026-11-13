package app.financecopilot.controller;

import app.financecopilot.ai.sql.TextToSqlService;
import app.financecopilot.dto.AskDtos.AskRequest;
import app.financecopilot.dto.AskDtos.AskResponse;
import app.financecopilot.dto.AskDtos.InsightResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@Tag(name = "AI")
public class AskController {

    private final TextToSqlService textToSql;
    private final InsightService insights;

    public AskController(TextToSqlService textToSql, InsightService insights) {
        this.textToSql = textToSql;
        this.insights = insights;
    }

    @PostMapping("/api/ask")
    @Operation(summary = "Answer a plain-English question with validated, user-scoped, read-only SQL")
    public AskResponse ask(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AskRequest request) {
        return textToSql.ask(user.id(), request.question().strip());
    }

    @GetMapping("/api/insights")
    @Operation(summary = "AI summary of a month, generated from aggregates only")
    public InsightResponse insight(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                   @RequestParam(defaultValue = "false") boolean refresh) {
        return insights.monthly(user.id(), month, refresh);
    }
}

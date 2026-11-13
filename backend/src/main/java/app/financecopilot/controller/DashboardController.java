package app.financecopilot.controller;

import app.financecopilot.dto.DashboardDtos.DashboardResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "KPIs, 6-month trend, spend by category, top merchants, anomalies, upcoming charges")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Parameter(description = "yyyy-MM; defaults to the latest month with data")
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return dashboardService.dashboard(user.id(), month);
    }
}

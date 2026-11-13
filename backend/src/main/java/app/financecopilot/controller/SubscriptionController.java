package app.financecopilot.controller;

import app.financecopilot.dto.SubscriptionDtos.SubscriptionResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @Operation(summary = "Detected recurring charges (weekly / monthly / yearly)")
    public List<SubscriptionResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptionService.list(user.id());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Re-run detection over the full transaction history")
    public List<SubscriptionResponse> refresh(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptionService.refresh(user.id());
    }
}

package app.financecopilot.security;

/**
 * Principal placed in the SecurityContext by {@link JwtAuthenticationFilter}.
 * Controllers receive it via {@code @AuthenticationPrincipal} and pass {@link #id()} down to every
 * service call, so all data access is scoped to the caller.
 */
public record AuthenticatedUser(Long id, String email) {
}

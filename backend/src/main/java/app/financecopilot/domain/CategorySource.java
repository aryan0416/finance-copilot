package app.financecopilot.domain;

/** How a transaction got its category. RULE + CACHE = categorised without an AI call. */
public enum CategorySource {
    RULE, CACHE, AI, USER, NONE
}

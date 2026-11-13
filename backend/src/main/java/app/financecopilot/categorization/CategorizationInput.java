package app.financecopilot.categorization;

import app.financecopilot.domain.Direction;

/** A transaction awaiting a category. {@code index} ties results back to the caller's list. */
public record CategorizationInput(int index, String merchant, String description, Direction direction) {

    /** Text that rules are matched against: normalised merchant plus the raw narration. */
    String matchText() {
        return merchant + " " + description;
    }
}

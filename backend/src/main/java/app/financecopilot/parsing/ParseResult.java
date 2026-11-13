package app.financecopilot.parsing;

import java.util.List;

public record ParseResult(List<ParsedRow> rows, List<RowFailure> failures) {

    /** A line that looked like a transaction but could not be parsed. */
    public record RowFailure(int lineNumber, String reason) {
    }
}

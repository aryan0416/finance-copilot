package app.financecopilot.parsing;

import app.financecopilot.domain.Direction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One transaction line as read from a statement, before normalisation and categorisation. */
public record ParsedRow(int lineNumber, LocalDate date, String description, BigDecimal amount,
                        Direction direction, BigDecimal balance) {
}

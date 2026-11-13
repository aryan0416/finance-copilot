package app.financecopilot.parsing;

/** The file is readable but doesn't look like a bank statement we can interpret. */
public class StatementFormatException extends RuntimeException {

    public StatementFormatException(String message) {
        super(message);
    }
}

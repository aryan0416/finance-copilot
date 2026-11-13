package app.financecopilot.parsing;

import app.financecopilot.domain.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PdfStatementParserTest {

    private final PdfStatementParser parser = new PdfStatementParser();

    @Test
    void parsesAmountWithDrCrSuffixAndBalance() {
        ParseResult result = parser.parseText("""
                STATEMENT OF ACCOUNT
                Date        Narration                       Amount        Balance
                01/11/2026  NEFT CR-ACME-SALARY NOV 2026   1,45,000.00 Cr  4,51,279.38
                02/11/2026  UPI/123456789012/RAMESH KUMAR     28,000.00 Dr  4,23,279.38
                            /rameshk88@oksbi/Flat rent NOV
                Closing Balance: 4,23,279.38
                """);

        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        ParsedRow salary = result.rows().get(0);
        assertThat(salary.direction()).isEqualTo(Direction.CREDIT);
        assertThat(salary.amount()).isEqualByComparingTo("145000.00");
        ParsedRow rent = result.rows().get(1);
        assertThat(rent.date()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(rent.direction()).isEqualTo(Direction.DEBIT);
        assertThat(rent.description()).endsWith("/rameshk88@oksbi/Flat rent NOV"); // wrapped line joined
    }

    @Test
    void footerTextIsNotAppendedToTheLastTransaction() {
        ParseResult result = parser.parseText("""
                12/11/2026  UPI/123456789012/ZEPTO/zepto@ybl/Order   873.00 Dr  4,20,000.00

                Closing Balance: 4,20,000.00
                This is a computer generated statement.
                """);

        assertThat(result.rows()).singleElement().extracting(ParsedRow::description)
                .isEqualTo("UPI/123456789012/ZEPTO/zepto@ybl/Order");
    }

    @Test
    void infersDirectionFromRunningBalanceWhenNoSuffix() {
        ParseResult result = parser.parseText("""
                01-Nov-2026  Opening line  100.00  1,100.00
                02-Nov-2026  Coffee        250.00    850.00
                03-Nov-2026  Refund         50.00    900.00
                """);

        assertThat(result.rows()).extracting(ParsedRow::direction)
                .containsExactly(Direction.DEBIT, Direction.CREDIT);
        assertThat(result.failures()).hasSize(1); // first row: no previous balance to compare against
    }

    @Test
    void parsesDebitCreditBalanceColumns() {
        ParseResult result = parser.parseText("""
                05/11/2026 POS NETFLIX.COM 649.00 0.00 9,351.00
                06/11/2026 INTEREST PAID 0.00 12.00 9,363.00
                """);

        assertThat(result.rows()).extracting(ParsedRow::direction).containsExactly(Direction.DEBIT, Direction.CREDIT);
    }

    @Test
    void parsesGeneratedSamplePdf() throws IOException {
        ParseResult result = parser.parse(Files.readAllBytes(Path.of("../sample-data/savings-statement-nov-2026.pdf")));

        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSizeGreaterThan(20);
        assertThat(result.rows().getFirst().description()).contains("SALARY NOV 2026");
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.date().getMonthValue()).isEqualTo(11));
    }
}

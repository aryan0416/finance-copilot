package app.financecopilot.parsing;

import app.financecopilot.domain.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    private ParseResult parse(String csv) throws IOException {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void detectsDebitCreditColumnsAfterPreamble() throws IOException {
        ParseResult result = parse("""
                MY BANK LTD
                Account No :,XXXX1234

                Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
                01/05/26,UPI/123/SWIGGY/swiggy@icici/Order,0001,01/05/26,222.00,,1000.00
                02/05/26,NEFT CR-ACME-SALARY,0002,02/05/26,,"1,45,000.00","1,46,000.00"

                *** End of Statement ***
                """);

        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        ParsedRow debit = result.rows().get(0);
        assertThat(debit.date()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(debit.direction()).isEqualTo(Direction.DEBIT);
        assertThat(debit.amount()).isEqualByComparingTo("222.00");
        assertThat(debit.balance()).isEqualByComparingTo("1000.00");
        ParsedRow credit = result.rows().get(1);
        assertThat(credit.direction()).isEqualTo(Direction.CREDIT);
        assertThat(credit.amount()).isEqualByComparingTo(new BigDecimal("145000.00"));
    }

    @Test
    void handlesSingleAmountColumnWithTypeAndSemicolons() throws IOException {
        ParseResult result = parse("""
                Transaction Date;Transaction Details;Amount (INR);Dr/Cr
                03-Sep-2026;DISNEY HOTSTAR MUMBAI IN;"1,499.00";Dr
                20-Sep-2026;PAYMENT RECEIVED - THANK YOU;"15,000.00";Cr
                """);

        assertThat(result.rows()).extracting(ParsedRow::direction).containsExactly(Direction.DEBIT, Direction.CREDIT);
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("1499.00");
    }

    @Test
    void signedAmountColumnInfersDirection() throws IOException {
        ParseResult result = parse("""
                Posting Date,Description,Amount,Balance
                2026-10-01,Coffee,-250.50,9749.50
                2026-10-02,Refund,100,9849.50
                """);

        assertThat(result.rows()).extracting(ParsedRow::direction).containsExactly(Direction.DEBIT, Direction.CREDIT);
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("250.50");
    }

    @Test
    void reportsBrokenRowsWithoutFailingTheFile() throws IOException {
        ParseResult result = parse("""
                Date,Description,Debit,Credit
                01/10/2026,Valid row,100.00,
                32/13/2026,Bad date,50.00,
                02/10/2026,No amount,,
                """);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.failures()).extracting(ParseResult.RowFailure::lineNumber).containsExactly(3, 4);
    }

    @Test
    void rejectsFilesWithoutRecognisableHeader() {
        assertThatThrownBy(() -> parse("foo,bar\n1,2\n")).isInstanceOf(StatementFormatException.class);
    }

    @Test
    void parsesGeneratedSixMonthSample() throws IOException {
        Path sample = Path.of("../sample-data/hdfc-style-savings-may-oct-2026.csv");
        ParseResult result = parser.parse(Files.readAllBytes(sample));

        assertThat(result.failures()).isEmpty();
        assertThat(result.rows()).hasSizeGreaterThan(400);
        assertThat(result.rows()).allSatisfy(r -> assertThat(r.amount()).isPositive());
    }
}

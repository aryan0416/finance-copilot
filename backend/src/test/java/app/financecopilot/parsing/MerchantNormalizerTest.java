package app.financecopilot.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "UPI/628712345678/SWIGGY/swiggy@icici/Payment                         | Swiggy",
            "UPI-ZOMATO LTD-ZOMATO@HDFCBANK-HDFC0000499-628712345678-PAYMENT        | Zomato",
            "UPI/869655555790/ANI TECHNOLOGIES PVT LTD/olacabs@ybl/Ola ride        | Ola",
            "POS 4591XXXXXXXX7712 NETFLIX.COM MUMBAI                              | Netflix",
            "UPI/111122223333/RELIANCE JIO/jio@sbi/Prepaid recharge               | Jio",
            "UPI/111122223333/JIOMART/jiomart@icici/Order                         | JioMart",
            "ACH D- GROWW MUTUAL FUND SIP-123456789                               | Groww",
            "POS 4591XXXXXXXX7712 INTERGLOBE AVIATION LTD                         | IndiGo",
            "UPI/628712345678/SHARMA GENERAL STORE/sharmastores@paytm/Paid via app | Sharma General Store",
            "UPI-DAILY FRESH DAIRY-DAILYFRESH.DAIRY@OKAXIS-YESB0000001-628712345678-WEEKLY MILK | Daily Fresh Dairy",
            "POS 4591XXXXXXXX7712 BLUE TOKAI COFFEE ROAS                          | Blue Tokai Coffee Roas",
            "NEFT CR-CITI0000002-ACME TECHNOLOGIES PVT LTD-SALARY MAY 2026         | Acme Technologies",
            "ATM WDL-HDFC ATM KORAMANGALA-123456-BANGALORE                        | ATM Withdrawal",
            "UPI/628712345678/ROHIT VERMA/rohitv@okaxis/Dinner split              | Rohit Verma",
            "INTEREST PAID TILL 30-JUN-2026                                       | Bank Interest",
            "PAYMENT RECEIVED - THANK YOU                                         | Card Payment",
    })
    void normalisesCommonIndianNarrations(String raw, String expected) {
        assertThat(normalizer.normalize(raw.strip())).isEqualTo(expected);
    }

    @Test
    void sameMerchantAcrossPaymentRailsGetsSameName() {
        String viaUpi = normalizer.normalize("UPI/123456789012/UBER INDIA SYSTEMS PVT LTD/uber.rides@axisbank/Trip");
        String viaCard = normalizer.normalize("POS 4591XXXXXXXX7712 UBER *TRIP HELP.UBER.COM");
        assertThat(viaUpi).isEqualTo("Uber").isEqualTo(viaCard);
    }

    @Test
    void fallsBackToVpaHandleWhenNoNameSegment() {
        assertThat(normalizer.normalize("UPI/123456789012/kiranstores@ybl/987654321098")).isEqualTo("Kiranstores");
    }

    @Test
    void shortBrandTokensNeedAWordBoundary() {
        assertThat(normalizer.normalize("UPI/123456789012/RAJIOV TRADERS/rt@ybl/Pay")).isEqualTo("Rajiov Traders");
    }

    @Test
    void blankInputIsUnknown() {
        assertThat(normalizer.normalize("  ")).isEqualTo("Unknown");
    }
}

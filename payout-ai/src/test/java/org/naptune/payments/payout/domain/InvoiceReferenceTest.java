package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naptune.payments.payout.domain.exception.PayoutException.InvalidInvoiceReferenceException;

// The normalisation table from build step 1: P-10, E-06, E-09, E-10.
class InvoiceReferenceTest {

    // P-10
    @ParameterizedTest
    @ValueSource(strings = {"INV-94018", "inv 94018", "INV/94018", " INV94018 "})
    void equivalentReferencesNormaliseTogether(String raw) {
        assertThat(InvoiceReference.normalize(raw)).isEqualTo("INV94018");
    }

    // E-06
    @Test
    void leadingZeroIsADifferentInvoice() {
        assertThat(InvoiceReference.normalize("INV-094018"))
                .isEqualTo("INV094018")
                .isNotEqualTo(InvoiceReference.normalize("INV-94018"));
    }

    // E-09: passes @NotBlank, fails normalisation.
    @Test
    void referenceWithNoLettersOrDigitsIsInvalid() {
        assertThatThrownBy(() -> InvoiceReference.normalize("---"))
                .isInstanceOf(InvalidInvoiceReferenceException.class);
    }

    // E-10
    @Test
    void fullWidthCharactersNormalise() {
        assertThat(InvoiceReference.normalize("ＩＮＶ－９４０１８")).isEqualTo("INV94018");
    }

    // E-10: 64 characters in, 125 after NFKC turns each "ﬁ" ligature into "FI".
    @Test
    void referenceThatGrowsPast64WhenFoldedIsInvalid() {
        String raw = "INV" + "ﬁ".repeat(61);
        assertThat(raw).hasSize(64);

        assertThatThrownBy(() -> InvoiceReference.normalize(raw))
                .isInstanceOf(InvalidInvoiceReferenceException.class);
    }

    @Test
    void exactly64CharactersAfterFoldingIsAccepted() {
        String raw = "INV-" + "9".repeat(61);
        assertThat(InvoiceReference.normalize(raw)).hasSize(64);
    }
}

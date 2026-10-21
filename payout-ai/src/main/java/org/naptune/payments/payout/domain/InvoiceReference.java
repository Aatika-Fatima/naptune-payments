package org.naptune.payments.payout.domain;

import java.text.Normalizer;
import java.util.Locale;
import org.naptune.payments.payout.domain.exception.PayoutException.InvalidInvoiceReferenceException;

public final class InvoiceReference {

    private InvoiceReference() {
    }

    // INV-94018, inv 94018 and INV/94018 are one invoice. INV-094018 is not:
    // digits are never dropped, so a leading zero stays significant.
    public static String normalize(String raw) {
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        String kept = folded.replaceAll("[^A-Z0-9]", "");
        // NFKC can lengthen a string (a ligature becomes two letters), so the
        // column limit is checked after folding, not only by @Size before it.
        if (kept.isEmpty() || kept.length() > 64) {
            throw new InvalidInvoiceReferenceException(raw);
        }
        return kept;
    }
}

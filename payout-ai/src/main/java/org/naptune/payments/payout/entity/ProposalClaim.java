package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.naptune.payments.payout.claims.ClaimSource;
import org.naptune.payments.payout.claims.ClaimSubject;
import org.naptune.payments.payout.claims.ClaimVerdict;

// A statement made about a proposal, by an operator, the agent or a retrieved
// document, and its verdict against the evidence the decision used. Claims
// never become evidence: the policy engine has no way to read this table.
@Entity
@Table(name = "proposal_claim")
public class ProposalClaim {

    static final int MAX_STATEMENT = 300;
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{6,}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    // No foreign key: claims are written inside the tool call, the turn row
    // only once the model has answered.
    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    // The decision whose evidence judged the claim; null if none existed yet.
    @Column(name = "decision_id", updatable = false)
    private Long decisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ClaimSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private ClaimSource source;

    // Required when source is RETRIEVED_DOCUMENT (ck_claim_document).
    @Column(name = "document_id", updatable = false, length = 64)
    private String documentId;

    @Column(nullable = false, updatable = false, length = MAX_STATEMENT)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ClaimVerdict verdict;

    // Filled by the column default.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ProposalClaim() {
    }

    private ProposalClaim(UUID paymentId, UUID turnId, Long decisionId, ClaimSubject subject, ClaimSource source,
                          String documentId, String statement, ClaimVerdict verdict) {
        this.paymentId = paymentId;
        this.turnId = turnId;
        this.decisionId = decisionId;
        this.subject = subject;
        this.source = source;
        this.documentId = documentId;
        this.statement = statement;
        this.verdict = verdict;
    }

    // The statement is masked before it is cut, so a long account number at the
    // limit cannot survive half-masked.
    public static ProposalClaim of(UUID paymentId, UUID turnId, Long decisionId, ClaimSubject subject,
                                   ClaimSource source, String documentId, String statement, ClaimVerdict verdict) {
        return new ProposalClaim(paymentId, turnId, decisionId, subject, source, documentId,
                truncate(mask(statement)), verdict);
    }

    // Digit runs of six or more keep their last four: 501002349931 -> XXXX9931.
    static String mask(String statement) {
        return LONG_DIGIT_RUN.matcher(statement)
                .replaceAll(run -> "XXXX" + run.group().substring(run.group().length() - 4));
    }

    private static String truncate(String statement) {
        return statement.length() <= MAX_STATEMENT ? statement : statement.substring(0, MAX_STATEMENT);
    }

    // Getters only: a claim and its verdict are recorded once.
    public Long getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public ClaimSubject getSubject() {
        return subject;
    }

    public ClaimSource getSource() {
        return source;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getStatement() {
        return statement;
    }

    public ClaimVerdict getVerdict() {
        return verdict;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

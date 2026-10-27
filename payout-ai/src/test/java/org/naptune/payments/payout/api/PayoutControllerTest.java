package org.naptune.payments.payout.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.repository.ApiIdempotencyRepository;
import org.naptune.payments.payout.repository.AuditRecordRepository;
import org.naptune.payments.payout.repository.EvidenceRecordRepository;
import org.naptune.payments.payout.repository.InvoiceAliasRepository;
import org.naptune.payments.payout.repository.PaymentDecisionRepository;
import org.naptune.payments.payout.repository.SupplierPaymentRepository;
import org.naptune.payments.payout.simulation.SimulatedWorld;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Build step 9 "Done when": the scenario request gets the documented response,
// and every failure is an answer with its own status, never a stray 500.
// Not @Transactional, for the same reason as ProposalServiceTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PayoutControllerTest {

    // The scenario, as a request: Agent A at 09:30.
    private static final String SCENARIO = """
            {
              "legalEntityId": "IN-01",
              "supplierId": "SUP-ORION",
              "invoiceReference": "INV-94018",
              "obligationId": "PO-94018-01",
              "purchaseOrderId": "PO-94018",
              "amountPaise": 487500000,
              "currency": "INR",
              "beneficiaryReference": "BANK-B-XXXX9931"
            }
            """;

    @Autowired
    MockMvcTester mvc;
    @Autowired
    SimulatedWorld world;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    InvoiceAliasRepository aliases;
    @Autowired
    ApiIdempotencyRepository idempotency;
    @Autowired
    EvidenceRecordRepository evidence;
    @Autowired
    PaymentDecisionRepository decisions;
    @Autowired
    AuditRecordRepository audit;

    @BeforeEach
    @AfterEach
    void clearTables() {
        audit.deleteAllInBatch();
        decisions.deleteAllInBatch();
        evidence.deleteAllInBatch();
        aliases.deleteAllInBatch();
        idempotency.deleteAllInBatch();
        payments.deleteAllInBatch();
        world.scenario();
    }

    @Test
    void scenarioRequestIsCreatedAndBlocked() {
        MvcTestResult result = propose("agent-a", "agent-a-request-123", SCENARIO);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("BLOCKED");
        assertThat(result).bodyJson().extractingPath("$.decision").isEqualTo("BLOCK");
        assertThat(result).bodyJson().extractingPath("$.reasonCodes").asArray().containsExactly(
                "BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE");
        assertThat(result).bodyJson().extractingPath("$.executionAllowed").isEqualTo(false);
        String id = paymentId(result);
        assertThat(result).hasHeader(HttpHeaders.LOCATION, "/api/payments/" + id);
    }

    @Test
    void retryOfADecidedPaymentIs200AndGetReturnsTheSameView() {
        String id = paymentId(propose("agent-a", "key-a", SCENARIO));

        MvcTestResult retry = propose("agent-a", "key-a", SCENARIO);
        assertThat(retry).hasStatus(HttpStatus.OK);
        assertThat(paymentId(retry)).isEqualTo(id);

        MvcTestResult get = mvc.get().uri("/api/payments/{id}", id).exchange();
        assertThat(get).hasStatus(HttpStatus.OK);
        assertThat(get).bodyJson().extractingPath("$.decision").isEqualTo("BLOCK");
    }

    @Test
    void reevaluationOfABlockedPaymentIs200() {
        String id = paymentId(propose("agent-a", "key-a", SCENARIO));

        MvcTestResult result = mvc.post().uri("/api/payments/{id}/evaluate", id)
                .header("X-Client-Id", "ops").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(decisions.countByPaymentId(UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void reevaluationOfAnAuthorizedPaymentIs409() {
        world.allClear();
        String id = paymentId(propose("agent-a", "key-a", SCENARIO.replace("BANK-B-XXXX9931", "BANK-A-XXXX7712")));

        MvcTestResult result = mvc.post().uri("/api/payments/{id}/evaluate", id)
                .header("X-Client-Id", "ops").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void reusedIdempotencyKeyWithAnotherBodyIs409() {
        propose("agent-a", "key-a", SCENARIO);

        MvcTestResult result = propose("agent-a", "key-a", SCENARIO.replace("INV-94018", "INV94018"));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void sameObligationWithDifferentTermsIs409NamingThePayment() {
        String id = paymentId(propose("agent-a", "key-a", SCENARIO));

        MvcTestResult result = propose("agent-b", "key-b", SCENARIO.replace("487500000", "485700000"));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.paymentId").isEqualTo(id);
    }

    @Test
    void invoiceReferenceWithNoLettersOrDigitsIs400() {
        MvcTestResult result = propose("agent-a", "key-a", SCENARIO.replace("\"INV-94018\"", "\"--/--\""));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertNoRows();
    }

    // N-01, N-02
    @Test
    void missingOrBlankHeaderIs400() {
        assertThat(mvc.post().uri("/api/payments").header("X-Client-Id", "agent-a")
                .contentType(MediaType.APPLICATION_JSON).content(SCENARIO).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(propose("agent-a", " ", SCENARIO)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/payments").header("Idempotency-Key", "key-a")
                .contentType(MediaType.APPLICATION_JSON).content(SCENARIO).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertNoRows();
    }

    // N-03, a sample: the full table belongs to the test-case step.
    @ParameterizedTest
    @ValueSource(strings = {
            "\"amountPaise\": 487500000|\"amountPaise\": 0",
            "\"amountPaise\": 487500000|\"amountPaise\": -1",
            "\"amountPaise\": 487500000|\"amountPaise\": null",
            "\"INR\"|\"inr\"",
            "\"INR\"|\"RUPEE\"",
            "\"SUP-ORION\"|\" \"",
    })
    void invalidFieldIs400(String replacement) {
        String[] parts = replacement.split("\\|");

        assertThat(propose("agent-a", "key-a", SCENARIO.replace(parts[0], parts[1])))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertNoRows();
    }

    // N-04. The fractional amount needs accept-float-as-int: false.
    @ParameterizedTest
    @ValueSource(strings = {"{\"legalEntityId\": ", "\"lots\"", "4875000.50"})
    void unreadableJsonIs400(String body) {
        String json = body.startsWith("{") ? body : SCENARIO.replace("487500000", body);

        assertThat(propose("agent-a", "key-a", json)).hasStatus(HttpStatus.BAD_REQUEST);
        assertNoRows();
    }

    // N-16
    @Test
    void unknownPaymentIs404AndAMalformedIdIs400() {
        assertThat(mvc.get().uri("/api/payments/{id}", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/payments/not-a-uuid").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    // N-21
    @Test
    void wrongMediaTypeOrMethodIsRefused() {
        assertThat(mvc.post().uri("/api/payments").header("X-Client-Id", "agent-a")
                .header("Idempotency-Key", "key-a").contentType(MediaType.TEXT_PLAIN).content(SCENARIO).exchange())
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(mvc.put().uri("/api/payments").contentType(MediaType.APPLICATION_JSON).content(SCENARIO).exchange())
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    private MvcTestResult propose(String clientId, String idempotencyKey, String body) {
        return mvc.post().uri("/api/payments")
                .header("X-Client-Id", clientId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String paymentId(MvcTestResult result) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.paymentId");
    }

    private void assertNoRows() {
        assertThat(payments.count()).as("payment").isZero();
        assertThat(aliases.count()).as("invoice_alias").isZero();
        assertThat(idempotency.count()).as("api_idempotency").isZero();
        assertThat(decisions.count()).as("payment_decision").isZero();
        assertThat(audit.count()).as("audit_record").isZero();
    }
}

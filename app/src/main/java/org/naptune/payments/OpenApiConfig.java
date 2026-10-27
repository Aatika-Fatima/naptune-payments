package org.naptune.payments;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI for the payout API. The controller stays free of OpenAPI
// annotations; what the UI needs beyond the generated schema is added here.
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI payoutApi() {
        return new OpenAPI().info(new Info()
                .title("Naptune payouts")
                .version("step 9")
                .description("""
                        Supplier payout proposals. X-Client-Id stands in for an authenticated principal.
                        Run app with the `scenario` profile to get the 09:30 decisions from the documentation;
                        docs/payout-api.http lists every test case."""));
    }

    // "Try it out" opens with the scenario request rather than "string" in every field.
    @Bean
    OpenApiCustomizer scenarioExample() {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("legalEntityId", "IN-01");
        scenario.put("supplierId", "SUP-ORION");
        scenario.put("invoiceReference", "INV-94018");
        scenario.put("obligationId", "PO-94018-01");
        scenario.put("purchaseOrderId", "PO-94018");
        scenario.put("amountPaise", 487500000L);
        scenario.put("currency", "INR");
        scenario.put("beneficiaryReference", "BANK-B-XXXX9931");
        return openApi -> {
            Schema<?> request = openApi.getComponents().getSchemas().get("ProposalRequest");
            if (request != null) {
                request.setExample(scenario);
            }
        };
    }
}

// File: relay-server/src/test/java/com/relay/server/unit/RelayPropertiesValidationTest.java
package com.relay.server.unit;

import com.relay.server.config.RelayProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RelayPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<RelayProperties>> validate(int leaseSeconds, int httpTotalTimeoutMs) {
        RelayProperties p = new RelayProperties();
        p.setAdminToken("t");
        p.setSecretEncryptionKey("a2V5");
        p.setLeaseSeconds(leaseSeconds);
        p.setHttpTotalTimeoutMs(httpTotalTimeoutMs);
        return validator.validate(p);
    }

    @Test
    void defaultsAreValid() {
        RelayProperties p = new RelayProperties();
        p.setAdminToken("t");
        p.setSecretEncryptionKey("a2V5");

        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest(name = "lease={0}s timeout={1}ms valid={2}")
    @CsvSource({
            "30, 5000,  true",    // 30 > 5 + 5
            "11, 5000,  true",    // just above 10
            "10, 5000,  false",   // equal to timeout + margin is not enough
            "9,  5000,  false",
            "5,  5000,  false",
            "36, 30000, true",    // 36 > 30 + 5
            "35, 30000, false",
            "6,  500,   true",    // 6 > 0.5 + 5
            "5,  500,   false"
    })
    void leaseMustExceedTimeoutPlusMargin(int lease, int timeoutMs, boolean valid) {
        assertThat(validate(lease, timeoutMs).isEmpty()).isEqualTo(valid);
    }

    @Test
    void failureMessageNamesTheSettingsAndTheReason() {
        var violations = validate(8, 5000);

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getMessage()).contains("relay.lease-seconds", "relay.http-total-timeout-ms", "5 second margin");
            assertThat(v.getPropertyPath().toString()).isEqualTo("leaseLongerThanHttpTimeout");
        });
    }

    @ParameterizedTest(name = "pool={0} cap={1} valid={2}")
    @CsvSource({"5,4,true", "5,5,true", "5,6,false", "2,2,true", "2,4,false", "1,1,true", "8,2,true"})
    void perEndpointCapMayNotExceedTheWorkerPool(int pool, int cap, boolean valid) {
        RelayProperties p = new RelayProperties();
        p.setAdminToken("t");
        p.setSecretEncryptionKey("a2V5");
        p.setWorkerPoolSize(pool);
        p.setPerEndpointMaxConcurrency(cap);

        var violations = validator.validate(p);

        assertThat(violations.isEmpty()).isEqualTo(valid);
        if (!valid) {
            assertThat(violations).singleElement().satisfies(v ->
                    assertThat(v.getMessage()).contains("per-endpoint-max-concurrency", "worker-pool-size"));
        }
    }

    @Test
    void perEndpointCapMustBeAtLeastOne() {
        RelayProperties p = new RelayProperties();
        p.setAdminToken("t");
        p.setSecretEncryptionKey("a2V5");
        p.setPerEndpointMaxConcurrency(0);

        assertThat(validator.validate(p)).extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("at least 1"));
    }
}

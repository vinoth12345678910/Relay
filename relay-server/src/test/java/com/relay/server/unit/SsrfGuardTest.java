// File: relay-server/src/test/java/com/relay/server/unit/SsrfGuardTest.java
package com.relay.server.unit;

import com.relay.server.common.ValidationException;
import com.relay.server.config.RelayProperties;
import com.relay.server.security.SsrfGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Literal IPs only, so nothing here touches DNS. */
class SsrfGuardTest {

    private static final String[] PRIVATE = {"http://10.0.0.5/x", "http://172.18.0.3:9000/x", "http://192.168.1.10/x",
            "http://100.64.0.1/x", "http://[fd00::1]/x"};
    private static final String[] ALWAYS_BLOCKED = {"http://127.0.0.1/x", "http://[::1]/x", "http://169.254.169.254/latest/meta-data",
            "http://169.254.0.1/x", "http://[fe80::1]/x", "http://0.0.0.0/x", "http://224.0.0.1/x"};

    private static SsrfGuard guard(boolean allowPrivate) {
        RelayProperties props = new RelayProperties();
        props.getSsrf().setAllowPrivateNetworks(allowPrivate);
        return new SsrfGuard(props);
    }

    @Test
    void strictByDefaultEvenWithoutProperties() {
        for (String url : PRIVATE) {
            assertThatThrownBy(() -> new SsrfGuard().validate(url)).isInstanceOf(ValidationException.class);
        }
        assertThat(new RelayProperties().getSsrf().isAllowPrivateNetworks()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://10.0.0.5/x", "http://172.18.0.3:9000/x", "http://192.168.1.10/x", "http://100.64.0.1/x", "http://[fd00::1]/x"})
    void privateRangesAreBlockedWhenTheFlagIsOff(String url) {
        assertThatThrownBy(() -> guard(false).validate(url)).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://10.0.0.5/x", "http://172.18.0.3:9000/x", "http://192.168.1.10/x", "http://100.64.0.1/x", "http://[fd00::1]/x"})
    void privateRangesAreAllowedWhenTheFlagIsOn(String url) {
        assertThatCode(() -> guard(true).validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/x", "http://[::1]/x", "http://169.254.169.254/latest/meta-data",
            "http://169.254.0.1/x", "http://[fe80::1]/x", "http://0.0.0.0/x", "http://224.0.0.1/x"})
    void loopbackLinkLocalMetadataAndFriendsAreBlockedEvenWithTheFlagOn(String url) {
        assertThatThrownBy(() -> guard(true).validate(url)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> guard(false).validate(url)).isInstanceOf(ValidationException.class);
    }

    @Test
    void publicAddressesAndSchemeRulesAreUnaffectedByTheFlag() {
        assertThatCode(() -> guard(true).validate("https://203.0.113.10/hook")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard(true).validate("ftp://10.0.0.5/x")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> guard(true).validate("http://user:pw@10.0.0.5/x")).isInstanceOf(ValidationException.class);
    }
}

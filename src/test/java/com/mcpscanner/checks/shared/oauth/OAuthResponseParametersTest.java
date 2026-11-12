package com.mcpscanner.checks.shared.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthResponseParametersTest {

    @Test
    void countsOccurrencesOfAName() {
        assertThat(new OAuthResponseParameters("code=a&state=s&code=b").count("code")).isEqualTo(2);
    }

    @Test
    void countsZeroForAnAbsentName() {
        assertThat(new OAuthResponseParameters("state=s").count("code")).isZero();
    }

    @Test
    void countsPercentEncodedNamesByTheirDecodedForm() {
        assertThat(new OAuthResponseParameters("state=a&st%61te=b").count("state")).isEqualTo(2);
    }

    @Test
    void countsNamesWithoutValues() {
        assertThat(new OAuthResponseParameters("code&state=s").count("code")).isEqualTo(1);
    }

    @Test
    void malformedEncodingCountsAsTooManyToTrust() {
        assertThat(new OAuthResponseParameters("%zz=1&code=a").count("code")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void singleReturnsTheDecodedValue() {
        assertThat(new OAuthResponseParameters("state=a%20b%2Bc+d").single("state")).contains("a b+c d");
    }

    @Test
    void singleIsEmptyWhenAbsent() {
        assertThat(new OAuthResponseParameters("code=a").single("state")).isEmpty();
    }

    @Test
    void singleIsEmptyWhenDuplicated() {
        assertThat(new OAuthResponseParameters("code=a&code=b").single("code")).isEmpty();
    }

    @Test
    void singleIsEmptyWhenTheNameHasNoValue() {
        assertThat(new OAuthResponseParameters("code&state=s").single("code")).isEmpty();
    }

    @Test
    void singleIsEmptyWhenTheValueIsBlank() {
        assertThat(new OAuthResponseParameters("code=%20&state=s").single("code")).isEmpty();
    }

    @Test
    void singleIsEmptyWhenTheValueIsMalformed() {
        assertThat(new OAuthResponseParameters("code=%zz").single("code")).isEmpty();
    }

    @Test
    void singleIsEmptyWhenAnyNameIsMalformed() {
        assertThat(new OAuthResponseParameters("%zz=1&code=a").single("code")).isEmpty();
    }
}

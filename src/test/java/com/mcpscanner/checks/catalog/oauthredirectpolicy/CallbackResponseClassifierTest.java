package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static com.mcpscanner.checks.catalog.oauthredirectpolicy.Outcomes.redirect;
import static org.assertj.core.api.Assertions.assertThat;

class CallbackResponseClassifierTest {

    private static final URI CALLBACK = URI.create("https://client.example.com/cb");
    private static final URI CALLBACK_WITH_QUERY = URI.create("https://client.example.com/cb?tenant=a");
    private static final String STATE = "state-1";

    private final CallbackResponseClassifier classifier = new CallbackResponseClassifier();

    private Delivery classify(String location) {
        return classifier.classify(redirect(location), CALLBACK, STATE);
    }

    @Test
    void codeInQueryWithMatchingStateIsCode() {
        assertThat(classify("https://client.example.com/cb?code=abc&state=state-1")).isEqualTo(Delivery.CODE);
    }

    @Test
    void errorInQueryWithMatchingStateIsError() {
        assertThat(classify("https://client.example.com/cb?error=access_denied&state=state-1"))
                .isEqualTo(Delivery.ERROR);
    }

    @Test
    void codeInFragmentIsCode() {
        assertThat(classify("https://client.example.com/cb#code=abc&state=state-1")).isEqualTo(Delivery.CODE);
    }

    @Test
    void errorInFragmentIsError() {
        assertThat(classify("https://client.example.com/cb#error=access_denied&state=state-1"))
                .isEqualTo(Delivery.ERROR);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void everyRedirectStatusCanDeliver(int status) {
        AuthorizationOutcome outcome = new AuthorizationOutcome(status,
                "https://client.example.com/cb?code=abc&state=state-1", true);

        assertThat(classifier.classify(outcome, CALLBACK, STATE)).isEqualTo(Delivery.CODE);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 304, 400, 500})
    void nonRedirectStatusIsRejectedEvenWithLocation(int status) {
        AuthorizationOutcome outcome = new AuthorizationOutcome(status,
                "https://client.example.com/cb?code=abc&state=state-1", true);

        assertThat(classifier.classify(outcome, CALLBACK, STATE)).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void redirectWithoutLocationIsRejected() {
        assertThat(classifier.classify(new AuthorizationOutcome(302, null, true), CALLBACK, STATE))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void overlongLocationIsRejected() {
        String location = "https://client.example.com/cb?code=abc&state=state-1&pad=" + "a".repeat(16_384);

        assertThat(classify(location)).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void malformedLocationIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=a b&state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void relativeLocationIsRejected() {
        assertThat(classify("/cb?code=abc&state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void locationWithUserinfoIsRejected() {
        assertThat(classify("https://attacker@client.example.com/cb?code=abc&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void differentHostIsRejected() {
        assertThat(classify("https://other.example.com/cb?code=abc&state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void differentPortIsRejected() {
        assertThat(classify("https://client.example.com:8443/cb?code=abc&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void differentPathIsRejected() {
        assertThat(classify("https://client.example.com/other?code=abc&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void differentSchemeIsRejected() {
        assertThat(classify("http://client.example.com/cb?code=abc&state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void schemeAndHostCompareCaseInsensitively() {
        assertThat(classify("HTTPS://CLIENT.example.com/cb?code=abc&state=state-1")).isEqualTo(Delivery.CODE);
    }

    @Test
    void callbackWithoutParametersIsRejected() {
        assertThat(classify("https://client.example.com/cb")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void mismatchedStateIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=abc&state=other")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void missingStateIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=abc")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void duplicatedStateIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=abc&state=state-1&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void percentEncodedStateMatchesItsDecodedValue() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?code=abc&state=a%20b");

        assertThat(classifier.classify(outcome, CALLBACK, "a b")).isEqualTo(Delivery.CODE);
    }

    @Test
    void duplicatedCodeIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=a&code=b&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void duplicatedErrorIsRejected() {
        assertThat(classify("https://client.example.com/cb?error=a&error=b&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void duplicatedCodeAlongsideASingleErrorIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=a&code=b&error=access_denied&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void duplicatedErrorAlongsideASingleCodeIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=abc&error=a&error=b&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void codeTogetherWithErrorIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=abc&error=access_denied&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void stateWithoutCodeOrErrorIsRejected() {
        assertThat(classify("https://client.example.com/cb?state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void blankCodeIsRejected() {
        assertThat(classify("https://client.example.com/cb?code=&state=state-1")).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void fragmentResponseWithUnexpectedQueryIsRejected() {
        assertThat(classify("https://client.example.com/cb?extra=1#code=abc&state=state-1"))
                .isEqualTo(Delivery.REJECTED);
    }

    @Test
    void registeredQueryFollowedByResponseParametersIsCode() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?tenant=a&code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.CODE);
    }

    @Test
    void registeredQueryWithFragmentResponseIsCode() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?tenant=a#code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.CODE);
    }

    @Test
    void droppedRegisteredQueryIsRejected() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void alteredRegisteredQueryIsRejected() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?tenant=b&code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void registeredQueryPrefixWithoutSeparatorIsRejected() {
        AuthorizationOutcome outcome = redirect("https://client.example.com/cb?tenant=ab&code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.REJECTED);
    }

    @Test
    void registeredQueryExtendedAlongsideFragmentResponseIsRejected() {
        AuthorizationOutcome outcome = redirect(
                "https://client.example.com/cb?tenant=a&extra=1#code=abc&state=state-1");

        assertThat(classifier.classify(outcome, CALLBACK_WITH_QUERY, STATE)).isEqualTo(Delivery.REJECTED);
    }
}

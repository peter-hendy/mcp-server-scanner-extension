package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;

import static com.mcpscanner.checks.catalog.oauthredirectpolicy.Outcomes.redirect;
import static com.mcpscanner.checks.catalog.oauthredirectpolicy.Outcomes.status;
import static org.assertj.core.api.Assertions.assertThat;

class CallbackDeliveryOracleTest {

    private static final String STATE = "state-1";
    private static final String REGISTERED = "https://client.example.com/cb";
    private static final String CANDIDATE = "https://client.example.com/other";
    private static final CallbackPair PAIR = new CallbackPair(URI.create(REGISTERED), URI.create(CANDIDATE));
    private static final AuthorizationOutcome CONTROL_CODE = codeTo(REGISTERED);

    private final CallbackDeliveryOracle oracle = new CallbackDeliveryOracle();

    private static AuthorizationOutcome codeTo(String callback) {
        return redirect(callback + "?code=abc&state=" + STATE);
    }

    private static AuthorizationOutcome errorTo(String callback) {
        return redirect(callback + "?error=access_denied&state=" + STATE);
    }

    @Nested
    class Classify {

        private Delivery classify(AuthorizationOutcome control, AuthorizationOutcome probe) {
            return oracle.classify(PAIR, STATE, control, probe);
        }

        @Test
        void codeToCandidateAfterControlDeliveryIsCode() {
            assertThat(classify(CONTROL_CODE, codeTo(CANDIDATE))).isEqualTo(Delivery.CODE);
        }

        @Test
        void errorToCandidateAfterControlDeliveryIsError() {
            assertThat(classify(CONTROL_CODE, errorTo(CANDIDATE))).isEqualTo(Delivery.ERROR);
        }

        @Test
        void controlErrorDeliveryStillConfirmsTheControl() {
            assertThat(classify(errorTo(REGISTERED), codeTo(CANDIDATE))).isEqualTo(Delivery.CODE);
        }

        @Test
        void incompleteControlIsInconclusive() {
            assertThat(classify(AuthorizationOutcome.incomplete(), codeTo(CANDIDATE))).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void incompleteProbeIsInconclusive() {
            assertThat(classify(CONTROL_CODE, AuthorizationOutcome.incomplete())).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void undeliveredControlIsInconclusiveEvenWhenTheProbeDelivers() {
            assertThat(classify(status(400), codeTo(CANDIDATE))).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void controlDeliveredToTheCandidateInsteadOfTheRegisteredUriIsInconclusive() {
            assertThat(classify(codeTo(CANDIDATE), codeTo(CANDIDATE))).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 403, 422})
        void explicitRejectionStatusIsRejected(int rejection) {
            assertThat(classify(CONTROL_CODE, status(rejection))).isEqualTo(Delivery.REJECTED);
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 401, 404, 500, 503})
        void otherNonRedirectStatusIsInconclusive(int other) {
            assertThat(classify(CONTROL_CODE, status(other))).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void redirectElsewhereIsInconclusive() {
            assertThat(classify(CONTROL_CODE, codeTo("https://auth.example.com/login")))
                    .isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void redirectToCandidateWithWrongStateIsInconclusive() {
            assertThat(classify(CONTROL_CODE, redirect(CANDIDATE + "?code=abc&state=other")))
                    .isEqualTo(Delivery.INCONCLUSIVE);
        }
    }

    @Nested
    class ClassifyWithNormalisation {

        private static final String TRAVERSAL = REGISTERED + "/../bypass";
        private static final String NORMALISED = "https://client.example.com/bypass";
        private static final CallbackPair TRAVERSAL_PAIR = new CallbackPair(URI.create(REGISTERED),
                URI.create(TRAVERSAL));
        private static final List<String> REGISTERED_ONLY = List.of(REGISTERED);

        private Delivery classify(CallbackPair pair, List<String> registered, AuthorizationOutcome probe) {
            return oracle.classifyWithNormalisation(pair, registered, STATE, CONTROL_CODE, probe);
        }

        @Test
        void deliveryToTheRawCandidateIsReported() {
            assertThat(classify(TRAVERSAL_PAIR, REGISTERED_ONLY, codeTo(TRAVERSAL))).isEqualTo(Delivery.CODE);
        }

        @Test
        void deliveryToTheNormalisedCandidateIsReported() {
            assertThat(classify(TRAVERSAL_PAIR, REGISTERED_ONLY, codeTo(NORMALISED))).isEqualTo(Delivery.CODE);
        }

        @Test
        void errorToTheNormalisedCandidateIsReported() {
            assertThat(classify(TRAVERSAL_PAIR, REGISTERED_ONLY, errorTo(NORMALISED))).isEqualTo(Delivery.ERROR);
        }

        @Test
        void explicitRejectionIsRejected() {
            assertThat(classify(TRAVERSAL_PAIR, REGISTERED_ONLY, status(400))).isEqualTo(Delivery.REJECTED);
        }

        @Test
        void redirectToNeitherFormIsInconclusive() {
            assertThat(classify(TRAVERSAL_PAIR, REGISTERED_ONLY, codeTo("https://client.example.com/elsewhere")))
                    .isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void candidateWithoutDotSegmentsIsInconclusive() {
            assertThat(classify(PAIR, REGISTERED_ONLY, codeTo(CANDIDATE))).isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void normalisedFormAlreadyRegisteredIsInconclusive() {
            assertThat(classify(TRAVERSAL_PAIR, List.of(REGISTERED, NORMALISED), codeTo(NORMALISED)))
                    .isEqualTo(Delivery.INCONCLUSIVE);
        }

        @Test
        void undeliveredControlIsInconclusive() {
            assertThat(oracle.classifyWithNormalisation(TRAVERSAL_PAIR, REGISTERED_ONLY, STATE, status(400),
                    codeTo(NORMALISED))).isEqualTo(Delivery.INCONCLUSIVE);
        }
    }
}

package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.scancheck.ScanCheckType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckKindTest {

    @Test
    void eachKindMapsToTheMatchingBurpScanCheckType() {
        assertThat(CheckKind.INSERTION_POINT.scanCheckType()).isEqualTo(ScanCheckType.PER_INSERTION_POINT);
        assertThat(CheckKind.REQUEST.scanCheckType()).isEqualTo(ScanCheckType.PER_REQUEST);
    }

    @Test
    void hostChecksAreInvokedPerRequestSoHostAuditsAloneDecidesWhenAHostIsDone() {
        assertThat(CheckKind.HOST.scanCheckType()).isEqualTo(ScanCheckType.PER_REQUEST);
    }

    @Test
    void eachKindHasAHumanReadableLabel() {
        assertThat(CheckKind.INSERTION_POINT.label()).isEqualTo("Per insertion point");
        assertThat(CheckKind.REQUEST.label()).isEqualTo("Per request");
        assertThat(CheckKind.HOST.label()).isEqualTo("Per host");
    }

    @Test
    void noKindRegistersWithBurpAsPerHost() {
        assertThat(CheckKind.values()).extracting(CheckKind::scanCheckType)
                .doesNotContain(ScanCheckType.PER_HOST);
    }
}

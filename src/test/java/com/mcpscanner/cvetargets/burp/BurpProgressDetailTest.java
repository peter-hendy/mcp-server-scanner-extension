package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CheckAccessPhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BurpProgressDetailTest {

    @Test
    void aBurpAuditDescribesItsStatusRequestsAndErrors() {
        BurpAuditDetails details = new BurpAuditDetails("extensions-only", "auditing", CompletedAudit.AuditEnd.UNFINISHED,
                3, 52000, 1, 40, 0, List.of(), 1000, 2000);

        assertThat(details.progress()).isEqualTo("Burp audit auditing: 52000 requests, 1 errors, 40 insertion points");
    }

    @Test
    void aCheckAccessRunDescribesItsPhaseAndItemsChecked() {
        assertThat(new BurpCheckAccessDetails(CheckAccessPhase.RUNNING, 4, 9, null).progress())
                .isEqualTo("Check access running: 4 of 9 items checked");
    }
}

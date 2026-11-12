package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import com.mcpscanner.checks.shared.auth.CatalogAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindingKindTest {

    @Test
    void eachKindReadsTheSharedCatalogAccessRatingForWhatItShows() {
        assertThat(FindingKind.KNOWN_SIBLING.rating()).isSameAs(CatalogAccess.ALTERNATE_ROUTE.rating());
        assertThat(FindingKind.ADVERTISED_MESSAGE_ROUTE.rating()).isSameAs(CatalogAccess.SESSION_CREDENTIAL.rating());
    }
}

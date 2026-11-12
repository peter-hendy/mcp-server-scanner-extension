package com.mcpscanner.checks.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckCatalogTest {

    @Test
    void ordersChecksByIdRegardlessOfFactoryOrder() {
        CheckCatalog catalog = CheckCatalog.of(List.of(
                services -> new RecordingChecks.Host("zeta", List.of()),
                services -> new RecordingChecks.Request("alpha", List.of()),
                services -> new RecordingChecks.InsertionPoint("mid", List.of())), CheckServices.standalone());

        assertThat(catalog.all()).extracting(check -> check.descriptor().id())
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void findsACheckById() {
        McpCheck check = new RecordingChecks.Request("alpha", List.of());
        CheckCatalog catalog = CheckCatalog.of(List.of(services -> check), CheckServices.standalone());

        assertThat(catalog.find("alpha")).containsSame(check);
        assertThat(catalog.find("missing")).isEmpty();
    }

    @Test
    void findsACheckByIdOnlyWhenItOffersTheCapability() {
        RequestCheck check = new RecordingChecks.Request("alpha", List.of());
        CheckCatalog catalog = CheckCatalog.of(List.of(services -> check), CheckServices.standalone());

        assertThat(catalog.find("alpha", RequestCheck.class)).containsSame(check);
        assertThat(catalog.find("alpha", HostCheck.class)).isEmpty();
    }

    @Test
    void handsTheSharedServicesToEveryFactory() {
        CheckServices services = CheckServices.standalone();
        AtomicReference<CheckServices> received = new AtomicReference<>();

        CheckCatalog.of(List.of(given -> {
            received.set(given);
            return new RecordingChecks.Host("a", List.of());
        }), services);

        assertThat(received.get()).isSameAs(services);
    }

    @Test
    void rejectsTwoChecksWithTheSameId() {
        assertThatThrownBy(() -> CheckCatalog.of(List.of(
                services -> new RecordingChecks.Request("dup", List.of()),
                services -> new RecordingChecks.Host("dup", List.of())), CheckServices.standalone()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void rejectsACheckWhoseDescriptorKindDiffersFromItsInterface() {
        assertThatThrownBy(() -> CheckCatalog.of(List.of(services -> new RecordingChecks.Request(
                RecordingChecks.descriptorFor("mislabelled", CheckKind.HOST), List.of())), CheckServices.standalone()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mislabelled");
    }

    @Test
    void emptyFactoryListGivesAnEmptyCatalog() {
        assertThat(CheckCatalog.of(List.of(), CheckServices.standalone()).all()).isEmpty();
    }

    @Test
    void discoverFindsChecksRegisteredOnlyThroughTheServiceFile() {
        CheckCatalog catalog = CheckCatalog.discover(CheckServices.standalone());

        assertThat(catalog.all()).extracting(check -> check.descriptor().id())
                .contains("fixture-reflected-marker", "fixture-resend-succeeds", "fixture-server-banner");
        assertThat(catalog.find("fixture-reflected-marker")).get()
                .extracting(check -> check.descriptor().kind()).isEqualTo(CheckKind.INSERTION_POINT);
        assertThat(catalog.find("fixture-resend-succeeds")).get()
                .extracting(check -> check.descriptor().kind()).isEqualTo(CheckKind.REQUEST);
        assertThat(catalog.find("fixture-server-banner")).get()
                .extracting(check -> check.descriptor().kind()).isEqualTo(CheckKind.HOST);
    }

    @Test
    void returnedCheckListIsImmutable() {
        CheckCatalog catalog = CheckCatalog.of(List.of(services -> new RecordingChecks.Host("a", List.of())),
                CheckServices.standalone());

        assertThatThrownBy(() -> catalog.all().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}

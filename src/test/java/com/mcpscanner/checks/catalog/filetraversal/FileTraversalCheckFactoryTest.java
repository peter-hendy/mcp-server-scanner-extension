package com.mcpscanner.checks.catalog.filetraversal;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileTraversalCheckFactoryTest {

    @Test
    void isDiscoveredByTheCatalogUnderItsId() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("file-traversal"))
                .containsInstanceOf(FileTraversalCheck.class);
    }

    @Test
    void missingFileNamesAreDistinctAndFilenameSafe() {
        MissingFileNames names = new MissingFileNames();

        assertThat(names.get()).matches("mcpscan-[0-9a-f]{10}").isNotEqualTo(names.get());
    }
}

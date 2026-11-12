package com.mcpscanner.checks.catalog;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogPackageNamingTest {

    private static final String CATALOG_PACKAGE = "com.mcpscanner.checks.catalog.";
    private static final String FIXTURE_PACKAGE = CATALOG_PACKAGE + "fixture";

    @Test
    void everyProductionCheckLivesInThePackageNamedAfterItsId() {
        List<McpCheck> productionChecks = CheckCatalog.discover(CheckServices.standalone()).all().stream()
                .filter(check -> !check.getClass().getPackageName().startsWith(FIXTURE_PACKAGE))
                .toList();

        assertThat(productionChecks).isNotEmpty().allSatisfy(check -> assertThat(check.getClass().getPackageName())
                .isEqualTo(CATALOG_PACKAGE + check.descriptor().id().replace("-", "")));
    }
}

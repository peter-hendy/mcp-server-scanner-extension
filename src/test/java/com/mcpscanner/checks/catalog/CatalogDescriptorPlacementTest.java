package com.mcpscanner.checks.catalog;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDescriptorPlacementTest {

    private static final String FIXTURE_PACKAGE = "com.mcpscanner.checks.catalog.fixture";

    @Test
    void everyProductionCheckTakesItsDescriptorFromASiblingDescriptorClass() {
        assertThat(productionChecks()).isNotEmpty().allSatisfy(check ->
                assertThat(descriptorConstants(siblingDescriptorClass(check))).containsExactly(check.descriptor()));
    }

    @Test
    void noProductionCheckClassDeclaresItsOwnDescriptor() {
        assertThat(productionChecks()).allSatisfy(check ->
                assertThat(descriptorConstants(check.getClass())).isEmpty());
    }

    private static List<McpCheck> productionChecks() {
        return CheckCatalog.discover(CheckServices.standalone()).all().stream()
                .filter(check -> !check.getClass().getPackageName().startsWith(FIXTURE_PACKAGE))
                .toList();
    }

    private static Class<?> siblingDescriptorClass(McpCheck check) {
        String checkName = check.getClass().getName();
        String descriptorName = checkName.substring(0, checkName.length() - "Check".length()) + "Descriptor";
        try {
            return Class.forName(descriptorName);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(checkName + " has no sibling " + descriptorName, missing);
        }
    }

    private static List<CheckDescriptor> descriptorConstants(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == CheckDescriptor.class)
                .map(CatalogDescriptorPlacementTest::read)
                .toList();
    }

    private static CheckDescriptor read(Field field) {
        try {
            field.setAccessible(true);
            return (CheckDescriptor) field.get(null);
        } catch (IllegalAccessException unreadable) {
            throw new AssertionError(unreadable);
        }
    }
}

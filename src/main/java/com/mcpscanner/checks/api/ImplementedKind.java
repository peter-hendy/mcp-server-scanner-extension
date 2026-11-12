package com.mcpscanner.checks.api;

public final class ImplementedKind implements CheckVisitor<CheckKind> {

    private static final ImplementedKind INSTANCE = new ImplementedKind();

    private ImplementedKind() {
    }

    public static CheckKind of(McpCheck check) {
        return check.accept(INSTANCE);
    }

    public static void requireDeclaredKindMatches(McpCheck check) {
        CheckDescriptor descriptor = check.descriptor();
        CheckKind implemented = of(check);
        if (descriptor.kind() != implemented) {
            throw new IllegalArgumentException("Check " + descriptor.id() + " declares kind "
                    + descriptor.kind() + " but implements the " + implemented + " interface");
        }
    }

    @Override
    public CheckKind visitInsertionPointCheck(InsertionPointCheck check) {
        return CheckKind.INSERTION_POINT;
    }

    @Override
    public CheckKind visitRequestCheck(RequestCheck check) {
        return CheckKind.REQUEST;
    }

    @Override
    public CheckKind visitHostCheck(HostCheck check) {
        return CheckKind.HOST;
    }
}

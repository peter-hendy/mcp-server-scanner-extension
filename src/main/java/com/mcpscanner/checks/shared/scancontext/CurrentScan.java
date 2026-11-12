package com.mcpscanner.checks.shared.scancontext;

import java.util.Optional;
import java.util.function.Supplier;

public final class CurrentScan {

    private static final ThreadLocal<ScanContext> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<ScanNotices> OUTSIDE_A_SCAN = new ThreadLocal<>();

    private CurrentScan() {
    }

    public static Optional<ScanContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean firstNotice(String notice) {
        ScanContext context = CURRENT.get();
        ScanNotices notices = context == null ? OUTSIDE_A_SCAN.get() : context.notices();
        return notices == null || notices.firstTime(notice);
    }

    public static <T> T noticing(ScanNotices notices, Supplier<T> work) {
        ScanNotices previous = OUTSIDE_A_SCAN.get();
        OUTSIDE_A_SCAN.set(notices);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                OUTSIDE_A_SCAN.remove();
            } else {
                OUTSIDE_A_SCAN.set(previous);
            }
        }
    }

    public static <T> T within(Optional<ScanContext> context, Supplier<T> work) {
        ScanContext previous = CURRENT.get();
        CURRENT.set(context.orElse(null));
        try {
            return work.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(ScanContext previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}

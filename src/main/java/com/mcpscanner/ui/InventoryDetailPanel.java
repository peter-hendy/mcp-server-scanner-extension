package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.detail.DetailSession;
import com.mcpscanner.ui.sendto.SendToBinding;

import javax.swing.JComponent;
import java.util.function.Supplier;

public interface InventoryDetailPanel<T> {
    JComponent component();
    void show(T item);
    void clear();
    void bindSendTo(SendToBinding<T> binding);
    void bindScan(ScanItemBinding<T> binding, Supplier<ScanMode> mode);
    void refreshActions();

    default void useSession(DetailSession session) {
    }
}

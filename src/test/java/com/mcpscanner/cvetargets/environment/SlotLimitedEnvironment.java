package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;

import java.net.URI;

public final class SlotLimitedEnvironment implements TargetEnvironment {

    private final TargetEnvironment delegate;
    private final MachineSlots slots;

    public SlotLimitedEnvironment(TargetEnvironment delegate, MachineSlots slots) {
        this.delegate = delegate;
        this.slots = slots;
    }

    @Override
    public RunningArm start(LoadedTarget target, TargetArm arm) {
        return reserve(target, arm).start();
    }

    @Override
    public ReservedArm reserve(LoadedTarget target, TargetArm arm) {
        ArmSlot slot = slots.acquire(target.displayName() + "/" + arm.name(),
                MachineSlots.slotsFor(target.manifest().footprintMb()));
        return new ReservedArm(() -> startHolding(slot, target, arm), slot::close);
    }

    private RunningArm startHolding(ArmSlot slot, LoadedTarget target, TargetArm arm) {
        try {
            return new SlottedArm(delegate.start(target, arm), slot);
        } catch (RuntimeException | Error startFailure) {
            releaseAfter(slot, startFailure);
            throw startFailure;
        }
    }

    private static void releaseAfter(ArmSlot slot, Throwable startFailure) {
        try {
            slot.close();
        } catch (RuntimeException releaseFailure) {
            startFailure.addSuppressed(releaseFailure);
        }
    }

    private record SlottedArm(RunningArm arm, ArmSlot slot) implements RunningArm {

        @Override
        public URI endpoint() {
            return arm.endpoint();
        }

        @Override
        public void close() {
            try (slot) {
                arm.close();
            }
        }
    }
}

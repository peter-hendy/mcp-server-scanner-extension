package com.mcpscanner.cvetargets;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.cvetargets.run.ArmResult;
import com.mcpscanner.cvetargets.run.ArmSchedule;
import com.mcpscanner.cvetargets.shared.ManifestArm;
import com.mcpscanner.cvetargets.shared.SharedTargetArm;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ScheduledArmTests {

    private final ArmSchedule schedule;
    private final List<TrackedArm> submitted = new CopyOnWriteArrayList<>();

    ScheduledArmTests(ArmSchedule schedule) {
        this.schedule = schedule;
    }

    List<DynamicNode> forTargets(List<LoadedTarget> targets,
                                 BiFunction<LoadedTarget, TargetArm, Optional<Supplier<ArmResult>>> plannedArms) {
        return targets.stream().<DynamicNode>map(target -> DynamicContainer.dynamicContainer(target.displayName(),
                target.manifest().arms().stream()
                        .map(arm -> DynamicTest.dynamicTest(arm.name(), submitted(target, arm, plannedArms)))
                        .toList()))
                .toList();
    }

    List<DynamicNode> forSharedArms(List<SharedTargetArm> sharedArms,
                                    Function<SharedTargetArm, Supplier<List<ArmResult>>> plannedArms) {
        Map<ManifestArm, CompletableFuture<ArmResult>> results = new LinkedHashMap<>();
        sharedArms.forEach(shared -> {
            CompletableFuture<List<ArmResult>> pending = schedule.submit(plannedArms.apply(shared));
            shared.members().forEach(member -> results.put(member,
                    tracked(member.target(), member.arm(), pending.thenApply(all -> resultOf(member, all)))));
        });
        Map<LoadedTarget, List<DynamicNode>> byTarget = new LinkedHashMap<>();
        results.forEach((member, pending) -> byTarget.computeIfAbsent(member.target(), target -> new ArrayList<>())
                .add(DynamicTest.dynamicTest(member.arm().name(), judged(member.target(), member.arm(), pending))));
        return byTarget.entrySet().stream().<DynamicNode>map(entry ->
                DynamicContainer.dynamicContainer(entry.getKey().displayName(), entry.getValue())).toList();
    }

    List<ArmResult> completedResults() {
        return submitted.stream().map(TrackedArm::result).toList();
    }

    private Executable submitted(LoadedTarget target, TargetArm arm,
                                 BiFunction<LoadedTarget, TargetArm, Optional<Supplier<ArmResult>>> plannedArms) {
        String check = target.manifest().check();
        CompletableFuture<ArmResult> pending = plannedArms.apply(target, arm).map(schedule::submit)
                .orElseGet(() -> CompletableFuture.completedFuture(ArmResult.notRunnable(check, target, arm, null,
                        "check " + check + " is not in the catalog yet")));
        return judged(target, arm, tracked(target, arm, pending));
    }

    private CompletableFuture<ArmResult> tracked(LoadedTarget target, TargetArm arm,
                                                 CompletableFuture<ArmResult> pending) {
        submitted.add(new TrackedArm(target, arm, pending));
        return pending;
    }

    private static ArmResult resultOf(ManifestArm member, List<ArmResult> results) {
        return results.stream()
                .filter(result -> result.check().equals(member.target().manifest().check())
                        && result.target().equals(member.target().manifest().id())
                        && result.arm().equals(member.arm().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no result for " + member.target().displayName() + "/"
                        + member.arm().name()));
    }

    private record TrackedArm(LoadedTarget target, TargetArm arm, CompletableFuture<ArmResult> pending) {

        ArmResult result() {
            try {
                return pending.join();
            } catch (CompletionException | CancellationException failed) {
                return ArmResult.crashed(target.manifest().check(), target, arm,
                        failed.getCause() == null ? failed : failed.getCause());
            }
        }
    }

    private static Executable judged(LoadedTarget target, TargetArm arm, CompletableFuture<ArmResult> pending) {
        return () -> {
            ArmResult result = pending.join();
            assumeTrue(result.notRunnable() == null, () -> "not runnable: " + result.notRunnable());
            assertThat(result.passed()).as("%s/%s: %s", target.displayName(), arm.name(), result.verdict()).isTrue();
        };
    }
}

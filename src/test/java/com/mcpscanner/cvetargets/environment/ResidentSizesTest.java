package com.mcpscanner.cvetargets.environment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentSizesTest {

    @Test
    void readsDockerStatsUsageInMegabytes() {
        assertThat(ResidentSizes.dockerUsageMb("45.5MiB / 17.5GiB")).isEqualTo(45);
        assertThat(ResidentSizes.dockerUsageMb("1.5GiB / 17.5GiB")).isEqualTo(1536);
        assertThat(ResidentSizes.dockerUsageMb("900KiB / 17.5GiB")).isZero();
        assertThat(ResidentSizes.dockerUsageMb("12B / 1GiB")).isZero();
    }

    @Test
    void sumsDockerStatsLines() {
        assertThat(ResidentSizes.dockerUsageTotalMb("100MiB / 2GiB\n\n28.2MiB / 2GiB\n")).isEqualTo(128);
    }

    @Test
    void sumsPsResidentSetKilobytesInMegabytes() {
        assertThat(ResidentSizes.psRssTotalMb(" 1048576\n 524288\n")).isEqualTo(1536);
        assertThat(ResidentSizes.psRssTotalMb("")).isZero();
    }

    @Test
    void ignoresUnreadableFigures() {
        assertThat(ResidentSizes.dockerUsageMb("-- / --")).isZero();
        assertThat(ResidentSizes.psRssTotalMb("abc")).isZero();
    }
}

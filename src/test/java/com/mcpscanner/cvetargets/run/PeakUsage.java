package com.mcpscanner.cvetargets.run;

public record PeakUsage(long harnessRssMb, long childJvmRssMb, long containersMb) {

    public static final PeakUsage NONE = new PeakUsage(0, 0, 0);

    public PeakUsage max(PeakUsage other) {
        return new PeakUsage(Math.max(harnessRssMb, other.harnessRssMb),
                Math.max(childJvmRssMb, other.childJvmRssMb), Math.max(containersMb, other.containersMb));
    }
}

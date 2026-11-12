package com.mcpscanner.cvetargets.environment;

record RecorderContainer(boolean running, String image) {

    static final String INSPECT_FORMAT = "{{.State.Running}} {{.Image}}";

    static RecorderContainer from(CommandResult inspected) {
        if (!inspected.succeeded()) {
            return new RecorderContainer(false, "");
        }
        String[] fields = inspected.output().strip().split(" ", 2);
        return new RecorderContainer(Boolean.parseBoolean(fields[0]), fields.length > 1 ? fields[1] : "");
    }

    boolean serves(String wantedImage) {
        return running && image.equals(wantedImage);
    }
}

package com.mcpscanner.engine;

public final class OutOfBandWatchText {

    private OutOfBandWatchText() {
    }

    public static String of(int watchedPayloads) {
        return watchedPayloads + (watchedPayloads == 1 ? " Collaborator payload" : " Collaborator payloads")
                + " still being watched for up to 15 min; late issues will be added to the site map";
    }
}

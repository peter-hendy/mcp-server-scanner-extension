package com.mcpscanner.auth.oauth.discovery;

import java.util.List;

public enum ChallengeStrategy {
    POST,
    STREAM_GET,
    POST_THEN_STREAM_GET;

    List<ChallengeSender> sendersFrom(ChallengeSender post, ChallengeSender streamGet) {
        return switch (this) {
            case POST -> List.of(post);
            case STREAM_GET -> List.of(streamGet);
            case POST_THEN_STREAM_GET -> List.of(post, streamGet);
        };
    }
}

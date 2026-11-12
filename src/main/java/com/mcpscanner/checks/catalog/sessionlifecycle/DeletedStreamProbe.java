package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.stream.StreamGetRequest;
import com.mcpscanner.checks.shared.stream.StreamOpener;
import com.mcpscanner.checks.shared.stream.StreamOpening;

import java.util.Optional;

final class DeletedStreamProbe {

    private final StreamOpener opener;
    private final HttpRequest seed;
    private final CheckLog log;

    DeletedStreamProbe(StreamOpener opener, HttpRequest seed, CheckLog log) {
        this.opener = opener;
        this.seed = seed;
        this.log = log;
    }

    Optional<StreamReuse> probe(String deletedId, String unissuedId) {
        StreamOpening afterDelete = opener.open(StreamGetRequest.from(seed, Optional.of(deletedId)));
        if (!afterDelete.opened()) {
            return Optional.empty();
        }
        StreamOpening control = opener.open(StreamGetRequest.from(seed, Optional.of(unissuedId)));
        if (!control.rejected()) {
            log.info("not reporting the deleted session's event stream: a stream with a never-issued ID got "
                    + control.status().stream().mapToObj(String::valueOf).findFirst().orElse("no response"));
            return Optional.empty();
        }
        return Optional.of(new StreamReuse(afterDelete.exchange(), control.exchange()));
    }
}

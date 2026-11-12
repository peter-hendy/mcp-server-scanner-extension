package com.mcpscanner.mcp;

import java.util.Set;

final class WriteLikeName {

    private static final Set<String> WRITE_VERBS = Set.of(
            "delete", "del", "remove", "rm", "update", "upsert", "create", "insert", "add", "send", "post", "put",
            "patch", "set", "write", "edit", "modify", "change", "save", "store", "pay", "charge", "refund",
            "transfer", "drop", "destroy", "purge", "erase", "clear", "reset", "revoke", "grant", "approve",
            "reject", "execute", "exec", "run", "invoke", "trigger", "publish", "upload", "import", "move", "rename",
            "cancel", "submit", "archive", "unarchive", "restore", "invite", "assign", "unassign", "enable",
            "disable", "start", "stop", "kill", "terminate", "apply", "merge", "push", "commit", "deploy", "mutate",
            "register", "subscribe", "unsubscribe", "unlock", "toggle", "unshare", "follow", "unfollow", "buy",
            "sell", "notify", "attach", "detach", "sync", "generate", "provision", "rotate", "login", "logout",
            "reload", "refresh", "rebuild", "reindex", "seed", "append", "prepend", "copy", "duplicate", "clone",
            "enqueue", "schedule", "capture", "end", "record", "increment", "decrement", "mark", "flag", "replace",
            "lock", "ban", "block", "close", "accept", "confirm", "share", "transition", "process", "checkout",
            "deactivate", "activate");

    private WriteLikeName() {
    }

    static boolean matches(String name) {
        return NameTokens.of(name).stream().anyMatch(WRITE_VERBS::contains);
    }
}

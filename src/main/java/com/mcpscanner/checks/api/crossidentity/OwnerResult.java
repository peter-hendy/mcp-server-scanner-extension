package com.mcpscanner.checks.api.crossidentity;

public record OwnerResult(ProbeKit kit, OperationReply owner) {

    public boolean returnedBy(String probe, OperationReply reply) {
        if (kit.transport().stopped()) {
            return rejected(probe + ":stopped");
        }
        if (!reply.success()) {
            return rejected(probe + ":peer_denied");
        }
        return owner.sameResult(reply) || rejected(probe + ":peer_result_differs");
    }

    public boolean markerConfirmedBy(OperationReply reply) {
        return kit.confirms(reply, kit.ownerMarker());
    }

    private boolean rejected(String decision) {
        kit.log().record(decision);
        return false;
    }
}

package com.mcpscanner.cvetargets.mining;

import burp.api.montoya.http.Http;
import com.mcpscanner.access.IdentityRequests;
import com.mcpscanner.access.mining.MiningReport;
import com.mcpscanner.access.mining.MiningSender;
import com.mcpscanner.access.mining.RoutedMiningSender;
import com.mcpscanner.checks.api.PrincipalRoster;

import java.util.function.Supplier;

public final class ArmMiner {

    private final MiningConnection connection;
    private final Http http;

    public ArmMiner(MiningConnection connection, Http http) {
        this.connection = connection;
        this.http = http;
    }

    public MiningSender senderFor(Supplier<PrincipalRoster> roster) {
        return new RoutedMiningSender(call -> connection.requestFor(call.message()), new IdentityRequests(roster),
                http);
    }

    public MiningReport mineInto(ArmUsers users) {
        return users.mine(connection.inventory(), senderFor(users.roster()));
    }
}

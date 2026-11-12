package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.cvetargets.environment.CommandRunner;
import com.mcpscanner.cvetargets.environment.Pause;
import com.mcpscanner.cvetargets.environment.ProcessCommandRunner;

import java.time.Clock;

public record IdentityScripts(ProvisionScript provision, ArmSetupScript setup) {

    public static IdentityScripts processes() {
        return using(new ProcessCommandRunner(), Pause.sleeping());
    }

    public static IdentityScripts using(CommandRunner commands, Pause pause) {
        return new IdentityScripts(new ProvisionScript(commands, pause, Clock.systemUTC()), new ArmSetupScript(commands));
    }
}

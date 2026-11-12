package com.mcpscanner.identity;

public sealed interface OwnedValue permits EnteredValue, MinedValue {

    String argument();

    String value();
}

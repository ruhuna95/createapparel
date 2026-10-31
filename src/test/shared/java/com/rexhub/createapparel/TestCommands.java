package com.rexhub.createapparel;

import net.minecraft.commands.CommandSourceStack;

public final class TestCommands {
    private TestCommands() { }

    public static int execute(CommandSourceStack source, String command) {
        int[] result = {0};
        source.getServer().getCommands().performPrefixedCommand(source.withCallback((success, value) -> result[0] = value), command);
        return result[0];
    }
}

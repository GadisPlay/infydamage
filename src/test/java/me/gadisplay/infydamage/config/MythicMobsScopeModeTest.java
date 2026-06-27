package me.gadisplay.infydamage.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicMobsScopeModeTest {

    @Test
    void noneAlwaysAllows() {
        assertTrue(MythicMobsScopeMode.NONE.allows("AncientGolem", Set.of()));
        assertTrue(MythicMobsScopeMode.NONE.allows("AncientGolem", Set.of("AncientGolem")));
    }

    @Test
    void whitelistOnlyAllowsListedTypes() {
        Set<String> listed = Set.of("AncientGolem");

        assertTrue(MythicMobsScopeMode.WHITELIST.allows("AncientGolem", listed));
        assertFalse(MythicMobsScopeMode.WHITELIST.allows("VoidWraith", listed));
    }

    @Test
    void blacklistExcludesOnlyListedTypes() {
        Set<String> listed = Set.of("AncientGolem");

        assertFalse(MythicMobsScopeMode.BLACKLIST.allows("AncientGolem", listed));
        assertTrue(MythicMobsScopeMode.BLACKLIST.allows("VoidWraith", listed));
    }
}

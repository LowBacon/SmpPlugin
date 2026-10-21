package com.bx.smpPlugin.core;

import com.bx.smpPlugin.api.PlaceholderResolver;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalPlaceholdersTest {

    @AfterEach
    void uninstall() {
        InternalPlaceholders.uninstall();
        Branding.load(null);
    }

    @Test
    void ownPlaceholdersAreFilledUnderEveryPrefix() {
        PlaceholderResolver resolver = mock(PlaceholderResolver.class);
        when(resolver.resolve(any(), eq("balance"))).thenReturn("1.5K");
        when(resolver.resolve(any(), eq("kills"))).thenReturn("12");
        InternalPlaceholders.install(resolver);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn("Alice");

        assertEquals("$1.5K | 12 | 12", InternalPlaceholders.apply("$%smp_balance% | %economy_kills% | %customsmp_kills%", player));
        assertEquals("Hi Alice", InternalPlaceholders.apply("Hi %player_name%", player));
    }

    @Test
    void anUnknownOwnPlaceholderShowsAsNothingNotAsCode() {
        PlaceholderResolver resolver = mock(PlaceholderResolver.class);
        when(resolver.resolve(any(), anyString())).thenReturn(null);
        InternalPlaceholders.install(resolver);
        assertEquals("Elo: ", InternalPlaceholders.apply("Elo: %smp_typo%", null));
    }

    @Test
    void aFailingResolverDoesNotBreakTheLine() {
        PlaceholderResolver resolver = mock(PlaceholderResolver.class);
        when(resolver.resolve(any(), anyString())).thenThrow(new IllegalStateException("not ready"));
        InternalPlaceholders.install(resolver);
        assertEquals("Money: ", InternalPlaceholders.apply("Money: %smp_balance%", null));
    }

    @Test
    void brandingTokensWorkWithoutAResolver() {
        assertEquals("Welcome to CustomSMP", InternalPlaceholders.apply("Welcome to {server_name}", null));
        assertEquals("other %luckperms_prefix% stays", InternalPlaceholders.apply("other %luckperms_prefix% stays", null));
    }

    @Test
    void leftoverPlaceholdersCanBeStripped() {
        assertEquals("Rank: ", InternalPlaceholders.stripUnresolved("Rank: %luckperms_prefix%"));
        assertEquals("50% off, 20%", InternalPlaceholders.stripUnresolved("50% off, 20%"));
        assertEquals("%prefix% stays", InternalPlaceholders.stripUnresolved("%prefix% stays"));
        assertEquals("a  b", InternalPlaceholders.stripUnresolved("a %vault_eco_balance% b"));
    }
}

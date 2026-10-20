package com.bx.smpPlugin.permissions;

import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The single list of every permission SmpPlugin checks.
 *
 * <p>The {@code permissions:} section of plugin.yml and {@code docs/permissions-reference.md}
 * are generated from this class ({@link PermissionRenderer}); {@code PermissionRegistryTest}
 * fails the build when either file is stale or when source code checks a node that is not
 * listed here. Regenerate with:</p>
 * <pre>mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true</pre>
 *
 * <p>Default policy: player features default to {@code true}; staff, admin, perks, bypasses and
 * destructive actions default to {@code false}. For migration, {@code smpplugin.admin} stays
 * default-op and grants staff tools, administration and operational bypasses, but never
 * destructive actions or security bypasses.</p>
 */
public final class PermissionRegistry {

    public static final String ADMIN_BUNDLE = "smpplugin.admin";
    public static final String LEGACY_STAFF_MODE_BUNDLE = "smpplugin.legacy.staff-mode-bundle";
    public static final String ROLE_HELPER = "smpplugin.role.helper";
    public static final String ROLE_MODERATOR = "smpplugin.role.moderator";
    public static final String ROLE_ADMIN = "smpplugin.role.admin";
    public static final String ROLE_OWNER = "smpplugin.role.owner";
    /** The ready-made staff roles, lowest first. */
    public static final List<String> ROLES = List.of(ROLE_HELPER, ROLE_MODERATOR, ROLE_ADMIN, ROLE_OWNER);

    /**
     * Numbered permissions resolved at runtime, e.g. {@code smpplugin.homes.10}.
     * They are documented but not declared, because the numbers are chosen by the server owner.
     *
     * @param pattern     the documented form
     * @param prefix      the literal prefix used in code
     * @param description what the number means
     */
    public record NumberedPermission(String pattern, String prefix, String description) {
    }

    /**
     * Auction House actions. Code builds these nodes as {@code "smpplugin.auctionhouse." + action},
     * which the source scan in PermissionRegistryTest cannot see, so they are listed here and tested.
     */
    public static final List<String> AUCTION_ACTIONS =
            List.of("use", "sell", "buy", "cancel", "claims", "my", "limit", "fastbuy", "fastsell");

    private static final Map<String, PermissionNode> NODES = new LinkedHashMap<>();
    private static final List<NumberedPermission> NUMBERED = List.of(
            new NumberedPermission("smpplugin.homes.<n>", "smpplugin.homes.",
                    "Maximum number of homes. The highest granted number wins; SETTINGS.HOME-DEFAULT applies otherwise."),
            new NumberedPermission("smpplugin.homes.page.<n>", "smpplugin.homes.page.",
                    "Number of home pages; each page adds a fixed number of homes."),
            new NumberedPermission("smpplugin.auctionhouse.limit.<n>", "smpplugin.auctionhouse.limit.",
                    "Maximum active Auction House listings. Also accepted: smpplugin.auctionhouse.<n> and legacy donutauction.limit.<n>."),
            new NumberedPermission("smpplugin.auctionhouse.<n>", "smpplugin.auctionhouse.",
                    "Older spelling of smpplugin.auctionhouse.limit.<n>."),
            new NumberedPermission("donutauction.limit.<n>", "donutauction.limit.",
                    "Legacy spelling of smpplugin.auctionhouse.limit.<n>."),
            new NumberedPermission("smpplugin.orders.limit.<n>", "smpplugin.orders.limit.",
                    "Maximum active buy orders."),
            new NumberedPermission("smpplugin.rtp.cooldown.<n>", "smpplugin.rtp.cooldown.",
                    "RTP cooldown in seconds. The lowest granted value wins (named nodes in rtp.yml RANK-COOLDOWNS count too); 0 removes the cooldown."),
            new NumberedPermission("smpplugin.rtp.priority.<n>", "smpplugin.rtp.priority.",
                    "Priority in the RTP waiting queue; higher goes first. Named nodes can be mapped in rtp.yml PRIORITY-QUEUE.PERMISSIONS.")
    );

    static {
        // ── Player features (default true) ─────────────────────────────────────────
        player("smpplugin.message", "Send private messages with /msg and reply with /reply.", "");
        player("smpplugin.message.toggle", "Turn receiving private messages on or off with /pm.", "");
        player("smpplugin.ignore", "Add and remove players from your ignore list.", "");
        player("smpplugin.helpop", "Ask online staff for help with /helpop.", "");
        player("smpplugin.report", "Report a player to staff with /report.", "");
        player("smpplugin.enderchest", "Open your own custom Ender Chest with /enderchest.", "");
        player("smpplugin.coinflip.use", "Open /coinflip: browse matches, view statistics, history, leaderboards, achievements and settings.",
                "New in 2.0.");
        player("smpplugin.coinflip.create", "Create public CoinFlip matches with in-game currency.", "New in 2.0.");
        player("smpplugin.coinflip.join", "Join CoinFlip matches and accept challenges.", "New in 2.0.");
        player("smpplugin.coinflip.challenge", "Send private CoinFlip challenges and rematches.", "New in 2.0.");
        player("smpplugin.coinflip.practice", "Flip practice coins (no currency).", "New in 2.0.");
        node("smpplugin.chat.color", "Keep colour codes typed into /msg and staff chat.", PermissionCategory.PERK,
                List.of(), "New in 2.0.0-alpha.5. Without it the codes are removed.");
        player("smpplugin.afk.away", "Mark yourself AFK where you stand with /away.", "New in 2.0.0-alpha.5.");
        player("smpplugin.findplayer", "See which dimension and biome another online player is in with /findplayer.",
                "New in 2.0. /findplayer had no permission check in 1.5; default true keeps that behaviour.");
        player("smpplugin.friends", "Use /friends to add, remove and follow friends.",
                "Declared in 2.0 with default true. It was undeclared in 1.5, which made it op-only unless granted.");
        player("smpplugin.lobby", "Use /lobby and /lobby survival.",
                "Declared in 2.0 with default true. It was undeclared in 1.5, which made it op-only unless granted.");
        player("smpplugin.safety.use", "Read the server safety book with /safety.",
                "Renamed from safety.use in 2.0; default changed from op-only to true.");
        player("smpplugin.auctionhouse.use", "Open and browse the Auction House.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.limit", "See your Auction House listing limit with /ah limit.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.sell", "List your own items on the Auction House.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.buy", "Buy Auction House listings.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.cancel", "Cancel your own active Auction House listings.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.claims", "Collect your own sold, expired and cancelled Auction House items.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");
        player("smpplugin.auctionhouse.my", "View your own Auction House listings.",
                "Declared in 2.0 with default true; previously undeclared (op-only unless granted).");

        // ── Rank perks (default false) ──────────────────────────────────────────────
        perk("smpplugin.auctionhouse.fastbuy", "Allow the quick-purchase setting that skips the Auction House buy confirmation.",
                "Declared in 2.0; previously undeclared (op-only unless granted).");
        perk("smpplugin.auctionhouse.fastsell", "Allow the quick-sell setting for Auction House listings.",
                "Declared in 2.0; previously undeclared (op-only unless granted).");
        perk("smpplugin.donutplus", "Show the CustomSmp+ badge (player setting).",
                "Declared in 2.0; previously undeclared (op-only unless granted).");
        perk("smpplugin.donutplusplus", "CustomSmp++ rank: unlocks the second page of Auction House listing slots.",
                "New in 2.0 with the merged Auction House.");
        perk("smpplugin.donutplusplusplus", "CustomSmp+++ rank: same Auction House slots as CustomSmp++.",
                "New in 2.0 with the merged Auction House.");
        // Example rank nodes named in the default rtp.yml (PRIORITY-QUEUE and RANK-COOLDOWNS).
        perk("smpplugin.rtp.priority.vip++", "RTP waiting-queue priority 30 (rtp.yml PRIORITY-QUEUE).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.priority.vip+", "RTP waiting-queue priority 20 (rtp.yml PRIORITY-QUEUE).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.priority.vip", "RTP waiting-queue priority 10 (rtp.yml PRIORITY-QUEUE).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.priority.high", "RTP waiting-queue priority 5 (rtp.yml PRIORITY-QUEUE).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.cooldown.vip++", "3-second RTP cooldown (rtp.yml RANK-COOLDOWNS).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.cooldown.vip+", "10-second RTP cooldown (rtp.yml RANK-COOLDOWNS).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.rtp.cooldown.vip", "15-second RTP cooldown (rtp.yml RANK-COOLDOWNS).", "New in 2.0 with the merged RTP.");
        perk("smpplugin.player.fly", "Fly with /fly where the server allows player flight (config FLY-SYSTEM).",
                "Declared in 2.0; previously undeclared (op-only unless granted).");
        perk("smpplugin.shards.everywhere", "Receive passive Shards Everywhere income.", "");
        perk("smpplugin.servers", "Open the network server status menu with /servers.", "");
        perk("smpplugin.hide.scramble", "Scramble your displayed name with /hide scramble.",
                "Declared in 2.0; previously undeclared (op-only unless granted).");
        perk("smpplugin.hide.disguise", "Disguise as a configured alias with /hide disguise.",
                "Declared in 2.0; previously undeclared (op-only unless granted).");

        // ── Staff tools (default false, in smpplugin.admin) ─────────────────────────
        staff("smpplugin.staff.mode", "Enter staff mode. Each hotbar tool still needs its own permission.",
                "In 1.5 this node also granted every staff tool. In 2.0 it only opens staff mode; grant "
                        + LEGACY_STAFF_MODE_BUNDLE + " for the old behaviour.");
        staff("smpplugin.staff.mode.vanish", "Use vanish (staff-mode hotbar and /vanish).", "");
        staff("smpplugin.staff.mode.stafflist", "Open the staff list.", "");
        staff("smpplugin.staff.mode.betterview", "Toggle better view from the staff-mode hotbar.", "");
        staff("smpplugin.staff.mode.randomtp", "Teleport to a random player from the staff-mode hotbar.", "");
        staff("smpplugin.staff.mode.spectator", "Switch to spectator from the staff-mode hotbar; sneak to switch back.",
                "New in 2.0.0-alpha.5.");
        staff("smpplugin.staff.mode.seevanished", "See vanished staff members.", "");
        staff("smpplugin.staff.mode.others", "Toggle staff mode for other players.", "");
        staff("smpplugin.staff.freeze", "Freeze and unfreeze players.", "");
        staff("smpplugin.staff.freeze.alert", "Receive freeze alerts.", "");
        staff("smpplugin.staff.fly", "Toggle flight for yourself or others with /fly.", "");
        staff("smpplugin.staff.heal", "Heal yourself or others with /heal.", "");
        staff("smpplugin.staff.feed", "Feed yourself or others with /feed.", "");
        staff("smpplugin.staff.god", "Toggle god mode with /god.", "Declared in 2.0; previously undeclared.");
        staff("smpplugin.staff.gamemode", "Change your own game mode.", "");
        staff("smpplugin.staff.gamemode.others", "Change another player's game mode.", "");
        staff("smpplugin.staff.chat.use", "Send and read staff chat.", "");
        staff("smpplugin.staff.chat.mute", "Mute global chat.", "");
        staff("smpplugin.staff.chat.unmute", "Unmute global chat.", "");
        staff("smpplugin.staff.chat.delay", "Set the global chat slow-mode delay.", "");
        staff("smpplugin.staff.chat.clear", "Clear global chat.", "");
        staff("smpplugin.staff.helpop.receive", "Receive /helpop alerts.", "");
        staff("smpplugin.staff.report.receive", "Receive /report alerts.", "");
        node("smpplugin.staff.alerts.receive", "Receive both /helpop and /report alerts.", PermissionCategory.STAFF,
                List.of("smpplugin.staff.helpop.receive", "smpplugin.staff.report.receive"), "");
        staff("smpplugin.staff.teleport", "Use the staff /teleport suite (/tp, /tphere, /tpall, /tp top).", "");
        staff("smpplugin.staff.rename", "Rename the held item with /rename.", "");
        staff("smpplugin.staff.alts", "See accounts that share an IP address with /alts.", "");
        staff("smpplugin.staff.invsee", "View another player's inventory (read-only).",
                "Upgrade spec name: smpplugin.staff.invsee.view.");
        staff("smpplugin.staff.profileviewer", "View player profiles and homes with /profileviewer.", "");
        staff("smpplugin.staff.punishments.view", "View punishment history with /punishments, or everyone's with no name.", "");
        staff("smpplugin.staff.punishments.vcmute", "Mute players in Simple Voice Chat with /vcmute, for a time or for good.",
                "New in 2.0.0-alpha.5.");
        staff("smpplugin.staff.punishments.vcunmute", "Lift voice mutes with /vcunmute.", "New in 2.0.0-alpha.5.");
        staff("smpplugin.staff.seehomes", "View another player's homes with /seehomes.", "New in 2.0.0-alpha.5.");
        staff("smpplugin.staff.punishments.ban", "Permanently ban players with /ban. Also grants /tempban.",
                "Grants smpplugin.staff.punishments.tempban as a child, matching 1.5.");
        staff("smpplugin.staff.punishments.tempban", "Temporarily ban players with /tempban.",
                "New in 2.0; in 1.5 /tempban used smpplugin.staff.punishments.ban.");
        staff("smpplugin.staff.punishments.mute", "Permanently mute players with /mute. Also grants /tempmute.",
                "Grants smpplugin.staff.punishments.tempmute as a child, matching 1.5.");
        staff("smpplugin.staff.punishments.tempmute", "Temporarily mute players with /tempmute.",
                "New in 2.0; in 1.5 /tempmute used smpplugin.staff.punishments.mute.");
        staff("smpplugin.staff.punishments.warn", "Warn players with /warn.",
                "New in 2.0; in 1.5 /warn used smpplugin.staff.punishments.create.");
        staff("smpplugin.staff.punishments.kick", "Kick players with /kick.",
                "New in 2.0; in 1.5 /kick used smpplugin.staff.punishments.create.");
        staff("smpplugin.staff.punishments.blacklist", "Blacklist players with /blacklist.", "");
        staff("smpplugin.staff.punishments.unban", "Lift bans with /unban (/pardon).", "");
        staff("smpplugin.staff.punishments.unmute", "Lift mutes with /unmute.", "");
        staff("smpplugin.staff.punishments.unblacklist", "Lift blacklists with /unblacklist.", "");
        staff("smpplugin.staff.punishments.offend", "Punish players with the preset offense rules in offenses.yml using /offend.",
                "New in 2.0.0-alpha.5.");
        staff("smpplugin.staff.fakeplayer", "Spawn fake players with /fakeplayer.", "Declared in 2.0; previously undeclared.");
        staff("smpplugin.staff.fakeplayer.alert", "Receive fake-player alerts.", "Declared in 2.0; previously undeclared.");
        staff("smpplugin.staff.spawnstash", "Spawn and manage bait stashes with /spawnstash.", "Declared in 2.0; previously undeclared.");
        staff("smpplugin.staff.spawnstash.alert", "Receive alerts when a bait stash is found.", "Declared in 2.0; previously undeclared.");
        staff("smpplugin.security.alerts", "Receive anti-cheat alerts and toggle them with /anticheat alerts.", "New in 2.0.");
        staff("smpplugin.security.inspect", "View anti-cheat status, check settings, flag history and evidence.", "New in 2.0.");
        staff("smpplugin.coinflip.alerts", "Receive CoinFlip storage and settlement alerts.", "New in 2.0.");
        staff("smpplugin.coinflip.inspect", "/coinflipadmin status, inspect, audit, reconcile report, debug and any player's statistics.",
                "New in 2.0.");

        // ── Administration (default false, in smpplugin.admin) ─────────────────────
        admin("smpplugin.admin.status", "View /smpplugin status (platform, storage, integrations, anti-Xray).", "New in 2.0.");
        admin("smpplugin.admin.reload", "Reload configuration with /smpplugin reload.", "");
        admin("smpplugin.admin.optimize", "View and change runtime optimization with /smpplugin optimize.", "");
        admin("smpplugin.admin.setup", "Use the /smpplugin setup wizard.", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.features", "Turn features on and off with /smpplugin features.", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.maintenance", "Turn maintenance mode on and off.", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.updatenotify", "See the update-available notice on join.",
                "New in 2.0; replaces the isOp() check and smpplugin.updatechecker.");
        admin("smpplugin.admin.shop", "Reload and validate shop settings.", "");
        admin("smpplugin.admin.orders", "Administer the Orders board.", "");
        admin("smpplugin.admin.auctionhouse", "Administer the Auction House; also passes every Auction House action check.", "");
        admin("smpplugin.admin.duels", "Manage duels and duel arenas.", "");
        admin("smpplugin.admin.ffa", "Manage FFA and FFA arenas.", "");
        admin("smpplugin.admin.clearlag", "Run /clearlag.", "");
        admin("smpplugin.admin.cuboid", "Manage cuboid regions.", "");
        admin("smpplugin.admin.warpmanager", "Manage warps with /warpmanager.", "");
        admin("smpplugin.admin.setwarp", "Create warps with /setwarp.", "");
        admin("smpplugin.admin.delwarp", "Delete warps with /delwarp.", "");
        admin("smpplugin.admin.portalmanager", "Manage RTP portals.", "");
        admin("smpplugin.admin.setspawn", "Set the server spawn.", "");
        admin("smpplugin.admin.afkzone", "Build and edit AFK zones with /afkzone.", "New in 2.0.0-alpha.5.");
        admin("smpplugin.admin.teleportareas.delete", "Delete spawn areas from the spawn menu.",
                "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.amethysttool", "Give amethyst tools.", "");
        admin("smpplugin.admin.worth", "Reload worth settings.", "");
        admin("smpplugin.admin.sellstats", "View and export sell statistics with /topsell. Resetting them needs smpplugin.admin.statswipe.",
                "New in 2.0 with the merged sell system.");
        admin("smpplugin.admin.enderchest", "Reload custom Ender Chest settings.", "");
        admin("smpplugin.admin.ecsee", "View another player's Ender Chest with /ecsee (read-only).",
                "Upgrade spec name: smpplugin.staff.ecsee.view.");
        admin("smpplugin.admin.freeze", "Reload freeze settings and freeze exempt players.", "");
        admin("smpplugin.admin.staffmode", "Reload staff-mode settings.", "");
        admin("smpplugin.admin.invsee", "Reload invsee settings.", "");
        admin("smpplugin.admin.spawner", "Give and manage managed spawners.", "");
        admin("smpplugin.admin.crate", "Create, edit and bind crates; give and take keys.", "");
        admin("smpplugin.admin.crate.reload", "Reload crate settings.", "");
        admin("smpplugin.admin.crate.keyall", "Start a key-all manually.", "");
        admin("smpplugin.admin.shards", "Add and remove shards; inspect Shards Everywhere.", "");
        admin("smpplugin.admin.addmoney", "Add money to a balance.", "");
        admin("smpplugin.admin.removemoney", "Remove money from a balance.", "");
        admin("smpplugin.admin.addkills", "Add kills to a player.", "");
        admin("smpplugin.admin.adddeaths", "Add deaths to a player.", "");
        admin("smpplugin.admin.addplaytime", "Add playtime to a player.", "");
        admin("smpplugin.admin.lobby.setlobby", "Set the lobby location with /setlobby.", "");
        admin("smpplugin.admin.lobby.border", "View or change the lobby border.", "");
        admin("smpplugin.admin.lobby.reload", "Reload lobby.yml.", "");
        admin("smpplugin.admin.lobby.debug", "Toggle lobby debug output.", "");
        admin("smpplugin.admin.logs", "View a player's activity log with /logs.", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.chatlog", "Browse logged public and team chat with /chatlog.", "New in 2.0.0-alpha.5.");
        admin("smpplugin.admin.spawnstash", "Administer bait stashes (reload, remove any).", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.admin.anvilmod", "Manage anvil moderation words (/amod) and bypass its filter.",
                "Renamed from anvilmod.admin in 2.0.");
        admin("smpplugin.admin.safety.reload", "Reload the safety book.", "Renamed from safety.reload in 2.0.");
        admin("smpplugin.admin.safety.give", "Give the safety book to a player.", "Renamed from safety.add in 2.0.");
        admin("smpplugin.admin.friends", "Use administrative /friends subcommands.", "Renamed from donutfriends.admin in 2.0.");
        admin("smpplugin.hide.admin", "Manage other players' hide and disguise state.", "Declared in 2.0; previously undeclared.");
        admin("smpplugin.security.manage", "Grant temporary anti-cheat exemptions, reset violation levels and reload anticheat.yml.",
                "New in 2.0.");
        admin("smpplugin.coinflip.admin.cancel", "Cancel and refund a waiting CoinFlip match (a reason is required and audited).",
                "New in 2.0.");
        admin("smpplugin.coinflip.admin.recover", "Retry stuck CoinFlip payouts and reconcile held matches from the journal.", "New in 2.0.");
        admin("smpplugin.coinflip.admin.reload", "Reload coinflip.yml and coinflip-messages.yml and refresh leaderboards.", "New in 2.0.");
        admin("smpplugin.coinflip.admin.toggle", "Pause or resume CoinFlip wagering for everyone.", "New in 2.0.");

        // ── Operational bypasses (default false, in smpplugin.admin) ───────────────
        operationalBypass("smpplugin.bypass.lobby", "Ignore lobby protection and the lobby border (needed to build the lobby).", "");
        operationalBypass("smpplugin.bypass.lobby.border", "Ignore only the lobby border.", "");
        operationalBypass("smpplugin.afk.kickbypass", "Never get kicked for being AFK (afk.yml IDLE-ACTIONS.KICK-SECONDS).", "New in 2.0.0-alpha.5.");
        perk("smpplugin.afk.statusbypass", "Never get the automatic [AFK] status after idling.", "New in 2.0.0-alpha.5.");
        operationalBypass("smpplugin.admin.maintenance.bypass", "Join and stay on the server during maintenance.",
                "Declared in 2.0; previously undeclared.");

        // ── Security and privacy bypasses (default false, never bundled) ───────────
        securityBypass("smpplugin.admin.punishments.exempt", "Cannot be punished by staff unless they hold the exempt bypass.",
                "New in 2.0.0-alpha.5. Not granted by smpplugin.admin, so moderators cannot make each other exempt.");
        securityBypass("smpplugin.admin.punishments.exempt.bypass", "Punish players who hold smpplugin.admin.punishments.exempt.",
                "New in 2.0.0-alpha.5.");
        securityBypass("smpplugin.ignore.bypass", "Message players who ignore you.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.message.bypass-disabled", "Message players who turned private messages off.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.staff.chat.bypass.mute", "Talk while global chat is muted.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.staff.chat.bypass.delay", "Ignore the chat slow-mode delay.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.staff.chat.bypass.filter", "Bypass the chat filter.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.staff.freeze.exempt", "Cannot be frozen by staff without smpplugin.admin.freeze.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.staff.alerts.bypass-cooldown", "Skip the /helpop and /report cooldowns.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.admin.spawner.seeall", "See spawners that anti-ESP hides from other players.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        securityBypass("smpplugin.hide.bypass", "See the real names behind hidden and disguised players.",
                "Declared in 2.0; previously undeclared.");
        securityBypass("smpplugin.preventdrop.bypass", "Drop items where item dropping is blocked.",
                "Declared in 2.0; previously undeclared.");
        securityBypass("smpplugin.staff.fakeplayer.bypass", "Not affected by fake-player restrictions.",
                "Declared in 2.0; previously undeclared.");
        securityBypass("smpplugin.staff.spawnstash.bypass", "Do not trigger bait-stash alerts.",
                "Declared in 2.0; previously undeclared.");
        securityBypass("smpplugin.bypass.anticheat.movement",
                "Skip anti-cheat movement checks (fly, speed, timer, no-fall, step, water walk, boat fly). Use is logged once per session.",
                "New in 2.0. Must be granted as the exact node; staff mode does not grant it.");
        securityBypass("smpplugin.bypass.anticheat.combat",
                "Skip anti-cheat combat checks (reach, wall hit, kill aura, autoclicker, anti-knockback, criticals). Use is logged.",
                "New in 2.0. Must be granted as the exact node.");
        securityBypass("smpplugin.bypass.anticheat.world",
                "Skip anti-cheat block checks (fast break, nuker, fast place, block reach, scaffold, through-wall, x-ray). Use is logged.",
                "New in 2.0. Must be granted as the exact node.");
        securityBypass("smpplugin.bypass.anticheat.player",
                "Skip anti-cheat inventory, auto-totem and cheat-client checks. Use is logged.",
                "New in 2.0. Must be granted as the exact node.");

        // ── Destructive or overwriting actions (default false, never bundled) ──────
        destructive("smpplugin.coinflip.admin.resolve",
                "Decide unknown CoinFlip transactions (applied or not); can create claims, so it is never bundled.", "New in 2.0.");
        destructive("smpplugin.coinflip.admin.resetstats", "Reset a player's CoinFlip statistics (a reason is required and audited).",
                "New in 2.0.");
        admin("smpplugin.admin.playerwipe", "Wipe one player's stats, homes, items and progress with /playerwipe (backed up first).",
                "New in 2.0.0-alpha.5.");
        admin("smpplugin.admin.playerunwipe", "Restore a wiped player from their backup with /playerunwipe.",
                "New in 2.0.0-alpha.5.");
        destructive("smpplugin.admin.serverwipe", "Run the season wipe workflow (/serverwipe).",
                "Declared in 2.0 with default false; previously undeclared (op-only).");
        destructive("smpplugin.admin.statswipe", "Wipe stats with /smpplugin statswipe.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.staff.punishments.delete", "Delete punishment records from history.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.admin.setmoney", "Overwrite a player's balance with /setmoney.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.admin.setkills", "Overwrite a player's kill count.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.admin.setdeaths", "Overwrite a player's death count.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.admin.setplaytime", "Overwrite a player's playtime.",
                "Removed from smpplugin.admin in 2.0; grant explicitly.");
        destructive("smpplugin.staff.invsee.modify", "Edit another player's inventory through invsee.",
                "Not part of smpplugin.admin. Upgrade spec name: smpplugin.staff.invsee.modify.");
        destructive("smpplugin.admin.ecsee.edit", "Edit another player's Ender Chest through /ecsee.",
                "Declared in 2.0 with default false; previously undeclared (op-only). Upgrade spec name: smpplugin.staff.ecsee.modify.");

        // ── 3.0: Halloween, daily rewards, events and the extra commands ──────────────
        player("smpplugin.halloween", "Open the Spooky Calendar with /halloween and open its doors.", "New in 3.0.");
        player("smpplugin.halloween.trickortreat", "Knock for a treat (or a harmless trick) with /trickortreat during the Halloween season.", "New in 3.0.");
        player("smpplugin.daily", "Claim the daily reward and keep a streak with /daily.", "New in 3.0.");
        player("smpplugin.challenges", "See and finish the daily challenges with /challenges.", "New in 3.0.");
        player("smpplugin.poll.vote", "Vote in polls started by staff.", "New in 3.0.");
        player("smpplugin.giveaway.join", "Join giveaways started by staff.", "New in 3.0.");
        player("smpplugin.mail", "Send and read /mail, including to offline players.", "New in 3.0.");
        player("smpplugin.fun.emotes", "Use /hug, /slap, /highfive, /poke, /wave and /cheer.", "New in 3.0.");
        player("smpplugin.fun.games", "Use /dice, /8ball, /rps and /rate.", "New in 3.0.");
        player("smpplugin.fun.sit", "Sit down where you stand with /sit.", "New in 3.0.");
        player("smpplugin.fun.hat", "Wear the item in your hand with /hat.", "New in 3.0.");
        player("smpplugin.trash", "Open a bin that deletes items with /trash.", "New in 3.0.");
        player("smpplugin.back", "Return to where you were before your last teleport with /back.", "New in 3.0.");
        player("smpplugin.seen", "See when a player was last online with /seen.", "New in 3.0.");
        player("smpplugin.coords", "Show your coordinates and share them with your team with /coords.", "New in 3.0.");
        player("smpplugin.suicide", "Respawn with /suicide (asks to confirm; not in combat).", "New in 3.0.");
        player("smpplugin.online", "List the players online with /online.", "New in 3.0.");
        player("smpplugin.uptime", "See how long the server has been up, and its TPS, with /uptime.", "New in 3.0.");
        player("smpplugin.calc", "Do sums with /calc.", "New in 3.0.");
        perk("smpplugin.fun.firework", "Launch a harmless firework with /firework.", "New in 3.0.");
        perk("smpplugin.fun.bypasscooldown", "No cooldown on emotes and the fun commands.", "New in 3.0.");
        perk("smpplugin.workbench.craft", "Open a crafting table anywhere with /craft.", "New in 3.0.");
        perk("smpplugin.workbench.anvil", "Open an anvil anywhere with /anvil.", "New in 3.0.");
        perk("smpplugin.workbench.grindstone", "Open a grindstone anywhere with /grindstone.", "New in 3.0.");
        perk("smpplugin.workbench.stonecutter", "Open a stonecutter anywhere with /stonecutter.", "New in 3.0.");
        perk("smpplugin.workbench.loom", "Open a loom anywhere with /loom.", "New in 3.0.");
        perk("smpplugin.workbench.smithing", "Open a smithing table anywhere with /smithing.", "New in 3.0.");
        perk("smpplugin.workbench.cartography", "Open a cartography table anywhere with /cartography.", "New in 3.0.");
        perk("smpplugin.back.death", "Use /back to return to where you died.", "New in 3.0.");
        perk("smpplugin.near", "List players near you with /near.", "New in 3.0.");
        perk("smpplugin.ptime", "Set your own time of day with /ptime.", "New in 3.0.");
        perk("smpplugin.pweather", "Set your own weather with /pweather.", "New in 3.0.");
        perk("smpplugin.repair", "Repair the item in your hand with /repair (cooldown in extras.yml).", "New in 3.0.");
        perk("smpplugin.repair.all", "Repair your whole inventory with /repair all.", "New in 3.0.");
        perk("smpplugin.repair.bypasscooldown", "No cooldown on /repair.", "New in 3.0.");
        perk("smpplugin.skull", "Get a player's head with /skull.", "New in 3.0.");
        perk("smpplugin.lore", "Edit the lore of the item in your hand with /lore.", "New in 3.0.");
        perk("smpplugin.glow", "Toggle a glowing outline with /glow.", "New in 3.0.");
        staff("smpplugin.staff.poll", "Start and end polls with /poll.", "New in 3.0.");
        staff("smpplugin.staff.giveaway", "Give away the item in your hand with /giveaway.", "New in 3.0.");
        staff("smpplugin.staff.chatgame", "Start and stop chat games with /chatgame.", "New in 3.0.");
        staff("smpplugin.staff.countdown", "Show a server-wide countdown with /countdown.", "New in 3.0.");
        staff("smpplugin.staff.speed", "Change walk and fly speed with /speed.", "New in 3.0.");
        staff("smpplugin.staff.whois", "Look up a player's details with /whois (the IP also needs smpplugin.staff.alts).", "New in 3.0.");
        staff("smpplugin.staff.notes", "Read and write private staff notes about players with /notes.", "New in 3.0.");
        staff("smpplugin.staff.socialspy", "See private messages between players with /socialspy.", "New in 3.0.");
        staff("smpplugin.staff.commandspy", "See the commands players run with /commandspy.", "New in 3.0.");
        admin("smpplugin.admin.halloween", "Edit Spooky Calendar prizes and use /halloween give, reset, preview and stats.", "New in 3.0.");
        admin("smpplugin.admin.giveaway.money", "Give away money that is created for the giveaway (/giveaway money).", "New in 3.0.");
        admin("smpplugin.admin.announcer", "List, send and pause the automatic announcements with /announcer.", "New in 3.0.");
        admin("smpplugin.admin.broadcast", "Send a server-wide announcement with /broadcast.", "New in 3.0.");
        admin("smpplugin.admin.time", "Set the time with /day and /night.", "New in 3.0.");
        admin("smpplugin.admin.weather", "Set the weather with /sun, /rain and /thunder.", "New in 3.0.");
        admin("smpplugin.admin.smite", "Strike a player with (harmless) lightning with /smite.", "New in 3.0.");
        admin("smpplugin.admin.butcher", "Remove nearby hostile mobs with /butcher.", "New in 3.0.");
        admin("smpplugin.admin.spawnmob", "Spawn mobs with /spawnmob.", "New in 3.0.");
        admin("smpplugin.admin.more", "Fill the held stack with /more.", "New in 3.0.");
        admin("smpplugin.admin.performance", "See TPS, memory, chunks and entities with /performance.", "New in 3.0.");
        admin("smpplugin.admin.spook", "Jump-scare a player (or everyone) with /spook.", "New in 3.0.");
        admin("smpplugin.admin.perms", "Inspect roles and permissions with /perms.", "New in 3.0.");

        // ── Legacy bundles kept so 1.5 grants behave the same ───────────────────────
        node("smpplugin.staff.punishments.create", "Legacy bundle: every punishment-creating action.",
                PermissionCategory.LEGACY,
                List.of("smpplugin.staff.punishments.warn", "smpplugin.staff.punishments.kick",
                        "smpplugin.staff.punishments.ban", "smpplugin.staff.punishments.tempban",
                        "smpplugin.staff.punishments.mute", "smpplugin.staff.punishments.tempmute",
                        "smpplugin.staff.punishments.blacklist"),
                "Kept from 1.5. Prefer the individual nodes.");
        node("smpplugin.staff.punishments.remove", "Legacy bundle: lift bans, mutes and blacklists.",
                PermissionCategory.LEGACY,
                List.of("smpplugin.staff.punishments.unban", "smpplugin.staff.punishments.unmute",
                        "smpplugin.staff.punishments.unblacklist"),
                "Kept from 1.5. Prefer the individual nodes.");
        node(LEGACY_STAFF_MODE_BUNDLE, "Everything smpplugin.staff.mode granted in 1.5.",
                PermissionCategory.LEGACY,
                List.of("smpplugin.staff.mode", "smpplugin.staff.freeze", "smpplugin.staff.freeze.alert",
                        "smpplugin.staff.fly", "smpplugin.staff.heal", "smpplugin.staff.feed",
                        "smpplugin.staff.gamemode", "smpplugin.staff.gamemode.others",
                        "smpplugin.staff.mode.vanish", "smpplugin.staff.mode.stafflist",
                        "smpplugin.staff.mode.betterview", "smpplugin.staff.mode.randomtp",
                        "smpplugin.staff.mode.seevanished", "smpplugin.staff.mode.others",
                        "smpplugin.staff.chat.use", "smpplugin.staff.teleport", "smpplugin.staff.rename",
                        "smpplugin.staff.alts", "smpplugin.staff.alerts.receive",
                        "smpplugin.staff.alerts.bypass-cooldown", "smpplugin.staff.invsee",
                        "smpplugin.staff.profileviewer", "smpplugin.staff.punishments.view",
                        "smpplugin.staff.punishments.create", "smpplugin.staff.punishments.remove"),
                "New in 2.0. Grant this where you granted smpplugin.staff.mode in 1.5 to keep the old tool set.");

        // ── Legacy names outside the smpplugin namespace ────────────────────────────
        legacy("anvilmod.admin", "smpplugin.admin.anvilmod");
        legacy("safety.use", "smpplugin.safety.use");
        legacy("safety.reload", "smpplugin.admin.safety.reload");
        legacy("safety.add", "smpplugin.admin.safety.give");
        legacy("smpplugin.updatechecker", "smpplugin.admin.updatenotify");
        legacy("donutfriends.admin", "smpplugin.admin.friends");
        for (String action : AUCTION_ACTIONS) {
            legacy("donutauction." + action, "smpplugin.auctionhouse." + action);
        }

        // ── 3.0 staff roles: give one node to a rank instead of picking nodes ───────
        List<String> helper = List.of(
                "smpplugin.staff.chat.use", "smpplugin.staff.helpop.receive", "smpplugin.staff.report.receive",
                "smpplugin.staff.alerts.receive", "smpplugin.staff.mode.stafflist", "smpplugin.staff.punishments.view",
                "smpplugin.staff.punishments.warn", "smpplugin.staff.punishments.kick", "smpplugin.staff.punishments.tempmute",
                "smpplugin.staff.chat.clear", "smpplugin.staff.notes", "smpplugin.staff.whois",
                "smpplugin.staff.poll", "smpplugin.staff.chatgame", "smpplugin.staff.countdown");
        role(ROLE_HELPER, "Helper role: staff chat, reports, warn, kick, temp-mute, staff notes, /whois and events.", helper);
        List<String> moderator = new ArrayList<>(List.of(ROLE_HELPER,
                "smpplugin.staff.mode", "smpplugin.staff.mode.vanish", "smpplugin.staff.mode.seevanished",
                "smpplugin.staff.mode.betterview", "smpplugin.staff.mode.randomtp", "smpplugin.staff.mode.spectator",
                "smpplugin.staff.freeze", "smpplugin.staff.freeze.alert", "smpplugin.staff.teleport",
                "smpplugin.staff.invsee", "smpplugin.staff.profileviewer", "smpplugin.staff.alts", "smpplugin.staff.seehomes",
                "smpplugin.staff.punishments.mute", "smpplugin.staff.punishments.ban", "smpplugin.staff.punishments.tempban",
                "smpplugin.staff.punishments.unmute", "smpplugin.staff.punishments.unban", "smpplugin.staff.punishments.offend",
                "smpplugin.staff.punishments.vcmute", "smpplugin.staff.punishments.vcunmute",
                "smpplugin.staff.chat.mute", "smpplugin.staff.chat.unmute", "smpplugin.staff.chat.delay",
                "smpplugin.staff.socialspy", "smpplugin.staff.commandspy", "smpplugin.staff.giveaway",
                "smpplugin.staff.speed", "smpplugin.admin.chatlog", "smpplugin.security.alerts"));
        role(ROLE_MODERATOR, "Moderator role: everything a helper has, plus staff mode, vanish, freeze, bans, mutes, invsee and spy.", moderator);
        role(ROLE_ADMIN, "Admin role: everything a moderator has plus every tool in smpplugin.admin (no destructive actions).",
                List.of(ROLE_MODERATOR, ADMIN_BUNDLE));
        List<String> owner = new ArrayList<>(List.of(ROLE_ADMIN));
        for (PermissionNode permission : List.copyOf(NODES.values())) {
            if (permission.category() == PermissionCategory.DESTRUCTIVE || permission.category() == PermissionCategory.PERK) {
                owner.add(permission.node());
            }
        }
        role(ROLE_OWNER, "Owner role: the admin role plus every destructive action (wipes, set-money) and every rank perk."
                + " Security bypasses (anti-cheat, hidden names) are still granted one by one.", owner);

        // ── The default-op admin bundle ─────────────────────────────────────────────
        List<String> adminChildren = new ArrayList<>();
        for (PermissionNode permission : NODES.values()) {
            if (permission.category().inAdminBundle()) {
                adminChildren.add(permission.node());
            }
        }
        PermissionNode adminBundle = new PermissionNode(ADMIN_BUNDLE,
                "Default-op administrator bundle: staff tools, administration and operational bypasses."
                        + " Excludes destructive actions and security bypasses.",
                PermissionCategory.BUNDLE, PermissionDefault.OP, adminChildren,
                "In 2.0 this no longer includes destructive actions or security bypasses.");

        // Declare the bundle first. Bukkit's YamlConfiguration reads dotted keys as nested paths, so a
        // "smpplugin.admin" entry placed after "smpplugin.admin.reload" would replace that whole subtree
        // for any tool that reads plugin.yml that way.
        Map<String, PermissionNode> ordered = new LinkedHashMap<>();
        ordered.put(ADMIN_BUNDLE, adminBundle);
        ordered.putAll(NODES);
        NODES.clear();
        NODES.putAll(ordered);
    }

    private PermissionRegistry() {
    }

    public static List<PermissionNode> all() {
        return List.copyOf(NODES.values());
    }

    public static Optional<PermissionNode> find(String node) {
        return node == null ? Optional.empty() : Optional.ofNullable(NODES.get(node.toLowerCase(Locale.ROOT)));
    }

    public static boolean isRegistered(String node) {
        return find(node).isPresent();
    }

    public static List<NumberedPermission> numberedPermissions() {
        return NUMBERED;
    }

    /** True when the node is declared or is a numbered form such as {@code smpplugin.homes.5}. */
    public static boolean isKnown(String node) {
        if (isRegistered(node)) {
            return true;
        }
        String lower = node.toLowerCase(Locale.ROOT);
        for (NumberedPermission numbered : NUMBERED) {
            if (lower.startsWith(numbered.prefix())
                    && Pattern.matches("\\d+", lower.substring(numbered.prefix().length()))) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, PermissionNode> asMap() {
        return Collections.unmodifiableMap(NODES);
    }

    // ── Builders ────────────────────────────────────────────────────────────────────

    private static void player(String node, String description, String history) {
        node(node, description, PermissionCategory.PLAYER, List.of(), history);
    }

    private static void perk(String node, String description, String history) {
        node(node, description, PermissionCategory.PERK, List.of(), history);
    }

    private static void staff(String node, String description, String history) {
        List<String> children = switch (node) {
            case "smpplugin.staff.punishments.ban" -> List.of("smpplugin.staff.punishments.tempban");
            case "smpplugin.staff.punishments.mute" -> List.of("smpplugin.staff.punishments.tempmute");
            default -> List.of();
        };
        node(node, description, PermissionCategory.STAFF, children, history);
    }

    private static void admin(String node, String description, String history) {
        node(node, description, PermissionCategory.ADMIN, List.of(), history);
    }

    private static void operationalBypass(String node, String description, String history) {
        node(node, description, PermissionCategory.OPERATIONAL_BYPASS, List.of(), history);
    }

    private static void securityBypass(String node, String description, String history) {
        node(node, description, PermissionCategory.SECURITY_BYPASS, List.of(), history);
    }

    private static void destructive(String node, String description, String history) {
        node(node, description, PermissionCategory.DESTRUCTIVE, List.of(), history);
    }

    private static void role(String node, String description, List<String> children) {
        node(node, description, PermissionCategory.BUNDLE, children,
                "New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes.");
    }

    private static void legacy(String oldNode, String replacement) {
        node(oldNode, "Legacy name for " + replacement + ".", PermissionCategory.LEGACY, List.of(replacement),
                "Kept so existing grants keep working. Grant " + replacement + " instead.");
    }

    private static void node(String node, String description, PermissionCategory category,
                             List<String> children, String history) {
        String key = node.toLowerCase(Locale.ROOT);
        if (NODES.containsKey(key)) {
            throw new IllegalStateException("Duplicate permission in registry: " + key);
        }
        PermissionDefault defaultValue = category.defaultTrue() ? PermissionDefault.TRUE : PermissionDefault.FALSE;
        NODES.put(key, new PermissionNode(key, description, category, defaultValue, children, history));
    }
}

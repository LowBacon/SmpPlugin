package com.bx.smpPlugin.dialogs;

import net.kyori.adventure.key.Key;

import java.util.Locale;

/**
 * Builds and validates the namespaced ids behind every dialog button.
 *
 * <p>A dialog action id is a vanilla resource location, so only {@code [a-z0-9._-]} survives in
 * the value. Screens embed their arguments in the id itself ({@code pay_target_<uuid>}), which
 * keeps the routing table flat and lets the config address a button that does not exist yet.
 */
public final class DialogActions {

    public static final String NAMESPACE = "smpplugin";

    // Screens
    public static final String MAIN_MENU = "main_menu";
    /** A COMMAND button on the generated pause-screen copy; the suffix is its index. */
    public static final String MAIN_COMMAND = "menucmd_";
    public static final String SETTINGS = "settings";
    public static final String SETTINGS_CATEGORY = "settings_category_";
    public static final String PAY_MENU = "pay_menu";
    public static final String PAY_ADD_PROMPT = "pay_add_prompt";
    public static final String PAY_ADD_EXECUTE = "execute_add_pay_player";
    public static final String PAY_TARGET = "pay_target_";
    public static final String PAY_CUSTOM = "pay_custom_";
    public static final String PAY_CUSTOM_CONTINUE = "pay_custom_continue_";
    public static final String PAY_AMOUNT = "pay_amount_";
    public static final String PAY_EXECUTE = "pay_execute_";
    public static final String PAY_REMOVE = "pay_remove_";
    public static final String STATS_MENU = "stats_menu";
    public static final String STATS_ADD_PROMPT = "stats_add_prompt";
    public static final String STATS_ADD_EXECUTE = "execute_add_stats_player";
    public static final String STATS_VIEW = "stats_view_";
    public static final String STATS_VIEW_FULL = "stats_view_full_";
    public static final String LEADERBOARDS_MENU = "leaderboards_menu";
    public static final String LEADERBOARD_CATEGORY = "lb_category_";
    public static final String LEADERBOARD_FULL = "lb_full_";
    /** The main menu button says "homes"; the back buttons say "homes_menu". Both open it. */
    public static final String HOMES = "homes";
    public static final String HOMES_MENU = "homes_menu";
    public static final String HOMES_PAGE = "homes_page_";
    public static final String HOME_CREATE_PROMPT = "create_home_prompt";
    public static final String HOME_CREATE_EXECUTE = "execute_create_home";
    public static final String HOME_MANAGE = "manage_home_";
    public static final String HOME_TELEPORT = "teleport_home_";
    public static final String HOME_RENAME_PROMPT = "rename_home_prompt_";
    public static final String HOME_RENAME_EXECUTE = "execute_rename_home_";
    public static final String HOME_DELETE_PROMPT = "delete_home_prompt_";
    public static final String HOME_DELETE_EXECUTE = "execute_delete_home_";
    public static final String HOME_ICON_PROMPT = "change_icon_prompt_";
    public static final String HOME_ICON_SEARCH = "search_home_icon_";
    public static final String HOME_ICON_SET = "set_home_icon_";
    public static final String HOME_ICON_DEFAULT = "default_home_icon_";
    public static final String HOME_TEAM = "team_home";
    public static final String FRIENDS = "friends";
    public static final String FRIENDS_FILTER = "friends_filter";
    public static final String FRIENDS_SEARCH_PROMPT = "friends_search_prompt";
    public static final String FRIENDS_SEARCH_EXECUTE = "execute_friends_search";
    public static final String FRIENDS_FOLLOW_PROMPT = "friends_follow_prompt";
    public static final String FRIENDS_FOLLOW_EXECUTE = "execute_follow_search";
    public static final String FRIENDS_FOLLOW_ADD = "friends_follow_add_";
    public static final String FRIENDS_VIEW = "friends_view_";
    public static final String FRIENDS_UNFOLLOW = "friends_unfollow_";
    public static final String FRIENDS_SETTINGS = "friends_settings_";
    public static final String FRIENDS_SETTING_TOGGLE = "friends_toggle_";
    public static final String TPA = "tpa";
    public static final String TPA_EXECUTE = "execute_tpa_request";
    public static final String TPA_ADD_PROMPT = "tpa_add_prompt";
    public static final String TPA_ADD_EXECUTE = "execute_add_tpa_player";
    public static final String TPA_TARGET = "tpa_target_";
    public static final String TPA_TO = "tpa_to_";
    public static final String TPA_HERE = "tpa_here_";
    public static final String RTP_QUEUE = "rtp_queue";
    public static final String RTP_QUEUE_ACCEPT = "rtp_queue_accept";
    public static final String RTP_QUEUE_DENY = "rtp_queue_deny";
    /** A COMMAND button: the suffix is a session id, not the command itself. */
    public static final String RUN_COMMAND = "run_";
    public static final String TOGGLE = "toggle_";
    public static final String CLOSE = "close";
    public static final String ORDERS = "orders";
    public static final String ORDERS_MENU = "orders_menu";
    public static final String QUICK_BUY_ITEM_SELECT = "qb_item_";
    public static final String QUICK_BUY_SEARCH = "qb_search_";
    public static final String QUICK_BUY_CONFIRM = "qb_confirm_";
    public static final String QUICK_BUY_CANCEL = "qb_cancel";
    public static final String QUICK_BUY_SEARCH_SUBMIT = "qb_srch_sub_";
    public static final String QUICK_BUY_SEARCH_CANCEL = "qb_srch_can_";
    public static final String QUICK_BUY_ENCHANT_LVL = "qb_en_lvl_";
    public static final String QUICK_BUY_ENCHANT_BACK = "qb_en_back_";
    public static final String QUICK_BUY_ENCHANT_SKIP = "qb_en_skip_";
    public static final String QUICK_BUY_ENCHANT_CONFIRM = "qb_en_conf_";
    public static final String ORD_SEARCH_GO = "ord_search_go";
    public static final String ORD_SEARCH_CAN = "ord_search_can";
    public static final String ORD_PICK_SRCH = "ord_pick_srch";
    public static final String ORD_PICK_CAN = "ord_pick_can";
    public static final String ORD_ITEM = "ord_item_";
    public static final String ORD_AMT_CAN = "ord_amt_can";
    public static final String ORD_AMT_NEXT = "ord_amt_next";
    public static final String ORD_PRICE_CAN = "ord_price_can";
    public static final String ORD_PRICE_GO = "ord_price_go";
    public static final String ORD_REV_CAN = "ord_rev_can";
    public static final String ORD_REV_ITEM = "ord_rev_item";
    public static final String ORD_REV_AMT = "ord_rev_amt";
    public static final String ORD_REV_PRICE = "ord_rev_price";
    public static final String ORD_REV_CREATE = "ord_rev_create";
    public static final String BOUNTY_SEARCH_GO = "bty_srch_go";
    public static final String BOUNTY_SEARCH_CAN = "bty_srch_can";
    public static final String AMETHYST_MENU = "amy_menu";
    public static final String AMETHYST_TYPES = "amy_types";
    public static final String AMETHYST_TYPE = "amy_type_";
    public static final String AMETHYST_PLAYERS = "amy_players";
    public static final String AMETHYST_PLAYER = "amy_player_";
    public static final String AMETHYST_PAGE = "amy_page_";
    public static final String AMETHYST_SEARCH = "amy_search";
    public static final String AMETHYST_SEARCH_GO = "amy_search_go";
    public static final String AMETHYST_SEARCH_CAN = "amy_search_can";
    public static final String AMETHYST_TIMES = "amy_times";
    public static final String AMETHYST_TIME = "amy_time_";
    public static final String AMETHYST_TIME_CUSTOM = "amy_time_custom";
    public static final String AMETHYST_TIME_CUSTOM_GO = "amy_time_go";


    private DialogActions() {
    }

    /** Builds a key inside the plugin namespace, sanitising the value first. */
    public static Key of(String value) {
        String sanitised = sanitise(value);
        return sanitised.isEmpty() ? null : Key.key(NAMESPACE, sanitised);
    }

    public static Key of(String prefix, String suffix) {
        return of(prefix + sanitise(suffix));
    }

    /**
     * Parses an id written in the config. A bare value is assumed to be ours; a value with a
     * namespace keeps it, so an operator can point a button at another plugin's dialog handler.
     */
    public static Key key(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String trimmed = configured.trim().toLowerCase(Locale.ROOT);
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return of(trimmed);
        }
        String namespace = sanitiseNamespace(trimmed.substring(0, separator));
        String value = sanitise(trimmed.substring(separator + 1));
        if (namespace.isEmpty() || value.isEmpty()) {
            return null;
        }
        try {
            return Key.key(namespace, value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** True when the key belongs to this plugin and so should be routed internally. */
    public static boolean isOurs(Key key) {
        return key != null && NAMESPACE.equals(key.namespace());
    }

    /** Strips the prefix off a dynamic action id, e.g. {@code pay_target_<uuid>}. */
    public static String argument(String value, String prefix) {
        return value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : null;
    }

    /** Reduces arbitrary text to the characters a resource location value allows. */
    public static String sanitise(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.' || character == '/') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static String sanitiseNamespace(String value) {
        return sanitise(value).replace('/', '.');
    }
}

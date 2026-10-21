package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.MatchState;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paginated match history, loaded one page at a time (never the whole history) off the server thread. Shows a
 * loading state while the page is read and an error state if storage is unavailable.
 */
public final class HistoryMenu extends CoinFlipMenu {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final UUID subject;
    private final String subjectName;
    private final int page;
    private final CoinFlipDao.HistoryFilter filter;
    private List<CoinFlipMatch> rows;
    private volatile boolean hidden;
    private boolean failed;

    public HistoryMenu(CoinFlipModule module, UUID viewer, UUID subject, String subjectName, int page,
                       CoinFlipDao.HistoryFilter filter) {
        super(module, viewer, "History: " + subjectName, 54);
        this.subject = subject;
        this.subjectName = subjectName;
        this.page = page;
        this.filter = filter;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        int perPage = Gui.INNER_54.length;
        // One extra row tells us whether a next page exists.
        module.repository().transaction(dao -> {
                    hidden = dao.loadStats(subject, subjectName).hidden();
                    return dao.loadHistory(subject, filter, perPage + 1, page * perPage);
                })
                .whenComplete((loaded, error) -> module.plugin().getSpigotScheduler().runGlobal(() -> {
                    rows = loaded;
                    failed = error != null;
                    refresh();
                }));
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        set(4, Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "History of " + subjectName,
                Gui.line(readable, "Filter", filter.name().toLowerCase()),
                Gui.line(readable, "Page", Integer.toString(page + 1)),
                Gui.MUTED + "Cancelled and expired matches never", Gui.MUTED + "count as wins or losses."));
        if (rows != null && hidden && !subject.equals(viewer) && !can(CoinFlipModule.PERM_INSPECT)) {
            set(22, Gui.item(Material.IRON_DOOR, Gui.MUTED + subjectName + "'s profile is private"));
            back(() -> openMenu(new MainMenu(module, viewer)));
            return;
        }
        if (failed) {
            set(22, Gui.item(Material.BARRIER, Gui.BAD + "History is unavailable", Gui.MUTED + "Please try again later."));
        } else if (rows == null) {
            set(22, Gui.item(Material.CLOCK, Gui.INFO + "Loading...", Gui.MUTED + "Reading your matches."));
        } else if (rows.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "Nothing here yet"));
        } else {
            for (int i = 0; i < Gui.INNER_54.length && i < rows.size(); i++) {
                set(Gui.INNER_54[i], historyItem(rows.get(i), readable));
            }
        }

        if (page > 0) {
            button(45, Gui.item(Material.ARROW, Gui.INFO + "Previous page"), () -> openMenu(
                    new HistoryMenu(module, viewer, subject, subjectName, page - 1, filter)));
        } else {
            back(() -> openMenu(new MainMenu(module, viewer)));
        }
        button(49, Gui.item(Material.HOPPER, Gui.INFO + "Filter: " + Gui.VALUE + filter.name().toLowerCase(),
                Gui.MUTED + "All, wins, losses, cancelled."), () -> openMenu(new HistoryMenu(module, viewer, subject, subjectName, 0,
                CoinFlipDao.HistoryFilter.values()[(filter.ordinal() + 1) % CoinFlipDao.HistoryFilter.values().length])));
        if (rows != null && rows.size() > Gui.INNER_54.length) {
            button(53, Gui.item(Material.ARROW, Gui.INFO + "Next page"), () -> openMenu(
                    new HistoryMenu(module, viewer, subject, subjectName, page + 1, filter)));
        }
    }

    private org.bukkit.inventory.ItemStack historyItem(CoinFlipMatch match, boolean readable) {
        List<String> lore = new ArrayList<>();
        boolean creator = subject.equals(match.creatorId());
        String opponent = creator ? match.opponentName() : match.creatorName();
        lore.add(Gui.line(readable, "Match", "#" + match.id()));
        lore.add(Gui.line(readable, "Opponent", opponent == null ? "-" : opponent));
        lore.add(Gui.line(readable, "Stake", money(match.stakeCents())));
        lore.add(Gui.line(readable, "Type", match.isPrivate() ? "challenge" : "public"));
        lore.add(Gui.line(readable, "Ended", match.settledAtMillis() > 0 ? DATE.format(Instant.ofEpochMilli(match.settledAtMillis())) : "-"));
        Material icon;
        String title;
        if (match.state() == MatchState.COMPLETED) {
            boolean won = subject.equals(match.winnerId());
            lore.add(Gui.line(readable, "Landed on", match.outcome() == null ? "-" : match.outcome().displayName()));
            lore.add(Gui.line(readable, won ? "Received" : "Lost", won ? money(match.payoutCents()) : money(match.stakeCents())));
            if (match.feeCents() > 0) {
                lore.add(Gui.line(readable, "Fee", money(match.feeCents())));
            }
            icon = won ? Material.EMERALD : Material.REDSTONE;
            title = won ? Gui.GOOD + "Won" : Gui.BAD + "Lost";
        } else {
            lore.add(Gui.line(readable, "Reason", match.endReason() == null ? "-" : match.endReason()));
            lore.add(Gui.MUTED + "The stake was returned.");
            icon = Material.GRAY_DYE;
            title = Gui.MUTED + (match.state() == MatchState.EXPIRED ? "Expired" : "Cancelled");
        }
        lore.add(Gui.line(readable, "Settlement", match.state().name().toLowerCase()));
        return Gui.item(icon, title + Gui.DIM + " #" + match.id(), lore);
    }

}

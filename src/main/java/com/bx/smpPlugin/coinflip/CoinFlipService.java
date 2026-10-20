package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import com.bx.smpPlugin.coinflip.storage.CoinFlipRepository;
import com.bx.smpPlugin.coinflip.storage.CoinFlipStorageException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CoinFlip use cases. Every public method is called on the server thread and completes its future on the server
 * thread. Money moves in three steps so that every change is either recorded or knowably not made:
 *
 * <ol>
 *   <li><b>Intent</b>: the journal row is stored before any balance changes.</li>
 *   <li><b>Change</b>: the online player's balance changes in memory (the economy's own storage).</li>
 *   <li><b>Apply</b>: the new balance, the journal row (now APPLIED) and the match state are written in one
 *       database transaction.</li>
 * </ol>
 *
 * If step 3 is rolled back, a debit is reversed in memory; a credit is retried. If the outcome of a commit is
 * unknown, the match is held as RECOVERY_REQUIRED and staff decide with the journal in front of them. Nothing is
 * ever deleted to hide an error, and a payout never happens without both stakes recorded as taken.
 */
public final class CoinFlipService {

    private static final int APPLY_RETRIES = 3;
    private static final int MAX_SWEEP_PER_TICK = 20;
    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** What happened, for the player who asked. */
    public record Outcome(boolean success, String messageKey, Map<String, String> placeholders, CoinFlipMatch match) {
        static Outcome ok(String key, Map<String, String> placeholders, CoinFlipMatch match) {
            return new Outcome(true, key, placeholders, match);
        }

        static Outcome fail(String key, Map<String, String> placeholders) {
            return new Outcome(false, key, placeholders, null);
        }

        static Outcome fail(String key) {
            return fail(key, Map.of());
        }
    }

    /** Request to create a public match or a private challenge. */
    public record CreateRequest(UUID player, String playerName, long stakeCents, CoinSide side, UUID invitee,
                                String inviteeName) {
    }

    private final CoinFlipRepository repository;
    private final EconomyGateway economy;
    private final CoinFlipNotifier notifier;
    private final RandomSource random;
    private final Executor mainThread;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final ZoneId zone;
    private final Supplier<CoinFlipConfig> config;
    private final ToLongFunction<UUID> playtimeMinutes;
    private final Logger logger;
    private final CoinFlipMetrics metrics;

    private final Map<String, CoinFlipMatch> active = new LinkedHashMap<>();
    private final Map<UUID, CoinFlipPreferences> preferences = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Set<UUID> deliveringClaims = new HashSet<>();
    private final Set<String> settling = new HashSet<>();
    private final RateLimiter actionCooldown = new RateLimiter();
    private final Map<UUID, Long> sessionStart = new HashMap<>();
    private final Map<UUID, Long> lastReminder = new HashMap<>();
    private boolean wageringPaused;
    private boolean recovered;

    /** Delayed execution on the server thread. */
    @FunctionalInterface
    public interface Scheduler {
        void runLater(long ticks, Runnable task);
    }

    public CoinFlipService(CoinFlipRepository repository, EconomyGateway economy, CoinFlipNotifier notifier,
                           RandomSource random, Executor mainThread, Scheduler scheduler, LongSupplier clock, ZoneId zone,
                           Supplier<CoinFlipConfig> config, ToLongFunction<UUID> playtimeMinutes, Logger logger,
                           CoinFlipMetrics metrics) {
        this.repository = repository;
        this.economy = economy;
        this.notifier = notifier;
        this.random = random;
        this.mainThread = mainThread;
        this.scheduler = scheduler;
        this.clock = clock;
        this.zone = zone;
        this.config = config;
        this.playtimeMinutes = playtimeMinutes;
        this.logger = logger;
        this.metrics = metrics;
    }

    // ── Queries ──────────────────────────────────────────────────────────────────

    public Collection<CoinFlipMatch> activeMatches() {
        return List.copyOf(active.values());
    }

    public Optional<CoinFlipMatch> findActive(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(active.get(normalizeId(id)));
    }

    /** Joinable public matches, newest first, capped at the configured listing size. */
    public List<CoinFlipMatch> publicListings() {
        long now = clock.getAsLong();
        return active.values().stream()
                .filter(match -> match.state() == MatchState.WAITING && !match.isPrivate() && !match.isExpired(now))
                .sorted(Comparator.comparingLong(CoinFlipMatch::createdAtMillis).reversed().thenComparing(CoinFlipMatch::id))
                .limit(config.get().maxListed())
                .toList();
    }

    public List<CoinFlipMatch> matchesOf(UUID player) {
        return active.values().stream()
                .filter(match -> match.involves(player) || player.equals(match.inviteeId()))
                .sorted(Comparator.comparingLong(CoinFlipMatch::createdAtMillis).reversed())
                .toList();
    }

    public long countInState(MatchState state) {
        return active.values().stream().filter(match -> match.state() == state).count();
    }

    public boolean wageringOpen() {
        CoinFlipConfig current = config.get();
        return current.enabled() && !current.maintenance() && !current.wageringBlocked() && !wageringPaused
                && repository.isReady() && recovered;
    }

    public boolean wageringPaused() {
        return wageringPaused;
    }

    public boolean recovered() {
        return recovered;
    }

    public CoinFlipPreferences preferences(UUID player) {
        CoinFlipConfig current = config.get();
        return preferences.getOrDefault(player, CoinFlipPreferences.defaults(current.defaultPreset(), current.readableDefault()));
    }

    public boolean preferencesLoaded(UUID player) {
        return preferences.containsKey(player);
    }

    public CoinFlipRepository repository() {
        return repository;
    }

    // ── Player lifecycle ─────────────────────────────────────────────────────────

    /** Loads preferences and pays any claims once the player is online. */
    public void playerJoined(UUID player) {
        loadPreferences(player);
        deliverClaims(player);
        if (config.get().allowPrivate()) {
            for (CoinFlipMatch match : active.values()) {
                if (player.equals(match.inviteeId()) && match.state() == MatchState.WAITING) {
                    notifier.challengeReceived(match);
                }
            }
        }
    }

    public void playerQuit(UUID player) {
        actionCooldown.forget(player);
        sessionStart.remove(player);
        lastReminder.remove(player);
        preferences.remove(player);
        // Open matches stay listed and expire normally; a refund for an offline creator becomes a claim.
    }

    public CompletableFuture<CoinFlipPreferences> loadPreferences(UUID player) {
        CoinFlipConfig current = config.get();
        CoinFlipPreferences defaults = CoinFlipPreferences.defaults(current.defaultPreset(), current.readableDefault());
        if (!repository.isReady()) {
            preferences.putIfAbsent(player, defaults);
            return CompletableFuture.completedFuture(defaults);
        }
        return onMain(repository.transaction(dao -> dao.loadPreferences(player)))
                .handle((stored, error) -> {
                    CoinFlipPreferences loaded = error != null || stored == null || stored.isEmpty()
                            ? defaults : CoinFlipPreferences.decode(stored.get(), defaults);
                    if (economy.isOnline(player)) {
                        preferences.put(player, loaded);
                    }
                    return loaded;
                });
    }

    public CompletableFuture<Boolean> savePreferences(UUID player, CoinFlipPreferences updated) {
        CoinFlipPreferences previous = preferences(player);
        preferences.put(player, updated);
        long now = clock.getAsLong();
        boolean hiddenChanged = previous.publicProfile() != updated.publicProfile();
        return onMain(repository.transaction(dao -> {
            dao.savePreferences(player, updated.encode(), now);
            if (hiddenChanged) {
                dao.setHidden(player, !updated.publicProfile());
            }
            return true;
        })).exceptionally(error -> false);
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    public CompletableFuture<Outcome> create(CreateRequest request) {
        CoinFlipConfig current = config.get();
        long now = clock.getAsLong();
        Optional<Outcome> refused = checkCanWager(request.player(), request.stakeCents(), now, current);
        if (refused.isPresent()) {
            return CompletableFuture.completedFuture(refused.get());
        }
        // A match held for staff review does not count: the player should not be locked out by a storage problem.
        long ownOpen = active.values().stream()
                .filter(match -> match.creatorId().equals(request.player()) && match.state().isActive()
                        && match.state() != MatchState.RECOVERY_REQUIRED)
                .count();
        if (ownOpen >= current.maxActivePerPlayer()) {
            return done(Outcome.fail("active-limit", vars("count", Long.toString(ownOpen))));
        }
        if (active.size() >= current.maxActiveTotal()) {
            return done(Outcome.fail("global-limit"));
        }
        if (request.invitee() != null) {
            Optional<Outcome> inviteProblem = checkInvitee(request);
            if (inviteProblem.isPresent()) {
                return done(inviteProblem.get());
            }
        }
        long cooldown = actionCooldown.tryAcquire(request.player(), now, current.cooldownMillis());
        if (cooldown > 0L) {
            return done(Outcome.fail("cooldown", vars("seconds", seconds(cooldown))));
        }

        boolean isPrivate = request.invitee() != null;
        long expiry = isPrivate ? current.challengeExpiryMillis() : current.expiryMillis();
        CoinFlipMatch match = new CoinFlipMatch(newMatchId(), request.player(), request.playerName(), request.side(),
                isPrivate, request.invitee(), request.inviteeName(), request.stakeCents(), current.feeBasisPoints(),
                now, now + expiry, MatchState.CREATING);
        CoinFlipTransaction stake = newTransaction(match, request.player(), request.playerName(),
                CoinFlipTransaction.Kind.STAKE, request.stakeCents(), CoinFlipTransaction.State.INTENT, "creator stake", now);
        active.put(match.id(), match);
        busy.add(request.player());
        metrics.attempt("create");

        long dayStart = Settlement.startOfDay(now, zone);
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        onMain(repository.transaction(dao -> {
            checkDailyLimits(dao, request.player(), request.stakeCents(), dayStart, true, current);
            dao.insertMatch(match);
            dao.insertTransaction(stake);
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                active.remove(match.id());
                busy.remove(request.player());
                CoinFlipStorageException storage = CoinFlipStorageException.from(error);
                if (!storage.definite()) {
                    // No balance changed; void the intent if it was stored.
                    abandon(match, stake, MatchState.CREATING, "intent commit uncertain");
                }
                result.complete(storageOutcome(storage));
                return;
            }
            takeCreatorStake(match, stake, result);
        });
        return result;
    }

    private void takeCreatorStake(CoinFlipMatch match, CoinFlipTransaction stake, CompletableFuture<Outcome> result) {
        UUID player = match.creatorId();
        if (!economy.isOnline(player) || !economy.withdraw(player, match.stakeCents())) {
            active.remove(match.id());
            busy.remove(player);
            abandon(match, stake, MatchState.CREATING, "stake could not be taken");
            result.complete(Outcome.fail("insufficient-funds", vars("amount", StakeMath.format(match.stakeCents()),
                    "balance", StakeMath.format(economy.balanceCents(player)))));
            return;
        }
        Optional<EconomyGateway.StoredBalance> balance = economy.storedBalance(player);
        long now = clock.getAsLong();
        onMain(repository.transaction(dao -> {
            dao.moveTransaction(stake.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.APPLIED, now,
                    "taken from balance");
            dao.writeBalance(player, balance.orElseThrow());
            dao.moveMatch(match.id(), MatchState.CREATING, MatchState.WAITING, 0L, null);
            return null;
        })).whenComplete((ignored, error) -> {
            busy.remove(player);
            if (error == null) {
                match.transition(MatchState.CREATING, MatchState.WAITING);
                metrics.success("create");
                debug("created #" + match.id() + " stake " + StakeMath.format(match.stakeCents()) + " by " + match.creatorName());
                markSession(player);
                CoinFlipPreferences prefs = preferences(player);
                preferences.put(player, prefs.withRecentStake(match.stakeCents()));
                notifier.matchesChanged();
                if (match.isPrivate()) {
                    notifier.challengeReceived(match);
                    result.complete(Outcome.ok("challenge-sent", matchVars(match), match));
                } else {
                    result.complete(Outcome.ok("match-created", matchVars(match), match));
                }
                return;
            }
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.definite()) {
                economy.deposit(player, match.stakeCents());
                active.remove(match.id());
                abandon(match, stake, MatchState.CREATING, "apply rolled back: " + storage.getMessage());
                result.complete(storageOutcome(storage));
            } else {
                holdForRecovery(match, "creator stake taken in memory; commit outcome unknown (" + storage.getMessage() + ")");
                result.complete(Outcome.fail("held-for-review", matchVars(match)));
            }
        });
    }

    private Optional<Outcome> checkInvitee(CreateRequest request) {
        if (!config.get().allowPrivate()) {
            return Optional.of(Outcome.fail("challenges-disabled"));
        }
        if (request.invitee().equals(request.player())) {
            return Optional.of(Outcome.fail("challenge-self"));
        }
        if (!economy.isOnline(request.invitee())) {
            return Optional.of(Outcome.fail("player-offline", vars("player", request.inviteeName())));
        }
        if (!preferences(request.invitee()).inviteNotifications()) {
            return Optional.of(Outcome.fail("challenge-not-accepting", vars("player", request.inviteeName())));
        }
        boolean duplicate = active.values().stream().anyMatch(match -> match.creatorId().equals(request.player())
                && request.invitee().equals(match.inviteeId()) && match.state().isActive());
        if (duplicate) {
            return Optional.of(Outcome.fail("challenge-duplicate", vars("player", request.inviteeName())));
        }
        return Optional.empty();
    }

    // ── Join ─────────────────────────────────────────────────────────────────────

    public CompletableFuture<Outcome> join(UUID player, String playerName, String matchId) {
        CoinFlipConfig current = config.get();
        long now = clock.getAsLong();
        CoinFlipMatch match = active.get(normalizeId(matchId));
        if (match == null) {
            return done(Outcome.fail("match-not-found", vars("id", normalizeId(matchId))));
        }
        if (match.creatorId().equals(player)) {
            return done(Outcome.fail("match-own", matchVars(match)));
        }
        if (match.isPrivate() && !player.equals(match.inviteeId())) {
            return done(Outcome.fail("not-your-challenge", matchVars(match)));
        }
        if (match.state() != MatchState.WAITING || match.isExpired(now)) {
            return done(Outcome.fail("match-not-joinable", matchVars(match)));
        }
        Optional<Outcome> refused = checkCanWager(player, match.stakeCents(), now, current);
        if (refused.isPresent()) {
            return done(refused.get());
        }
        long cooldown = actionCooldown.tryAcquire(player, now, current.cooldownMillis());
        if (cooldown > 0L) {
            return done(Outcome.fail("cooldown", vars("seconds", seconds(cooldown))));
        }
        if (!match.transition(MatchState.WAITING, MatchState.JOINING)) {
            return done(Outcome.fail("match-not-joinable", matchVars(match)));
        }
        match.setPendingOpponent(player, playerName);
        busy.add(player);
        metrics.attempt("join");
        CoinFlipTransaction stake = newTransaction(match, player, playerName, CoinFlipTransaction.Kind.STAKE,
                match.stakeCents(), CoinFlipTransaction.State.INTENT, "opponent stake", now);
        long dayStart = Settlement.startOfDay(now, zone);
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        onMain(repository.transaction(dao -> {
            checkDailyLimits(dao, player, match.stakeCents(), dayStart, false, current);
            dao.reserveOpponent(match.id(), player, playerName);
            dao.insertTransaction(stake);
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                busy.remove(player);
                CoinFlipStorageException storage = CoinFlipStorageException.from(error);
                if (!storage.definite()) {
                    releaseJoin(match, stake, "intent commit uncertain");
                } else {
                    match.clearPendingOpponent();
                    match.transition(MatchState.JOINING, MatchState.WAITING);
                }
                result.complete(storage.reason() == CoinFlipStorageException.Reason.CONFLICT
                        ? Outcome.fail("match-not-joinable", matchVars(match)) : storageOutcome(storage));
                return;
            }
            takeOpponentStake(match, stake, result);
        });
        return result;
    }

    private void takeOpponentStake(CoinFlipMatch match, CoinFlipTransaction stake, CompletableFuture<Outcome> result) {
        UUID player = stake.playerId();
        if (!economy.isOnline(player) || !economy.withdraw(player, match.stakeCents())) {
            busy.remove(player);
            releaseJoin(match, stake, "stake could not be taken");
            result.complete(Outcome.fail("insufficient-funds", vars("amount", StakeMath.format(match.stakeCents()),
                    "balance", StakeMath.format(economy.balanceCents(player)))));
            return;
        }
        // The result is drawn only now, after both stakes are taken, and stored in the same transaction.
        CoinSide outcome = random.flip();
        String outcomeId = random.newOutcomeId();
        long now = clock.getAsLong();
        UUID winner = outcome == match.creatorSide() ? match.creatorId() : player;
        Optional<EconomyGateway.StoredBalance> balance = economy.storedBalance(player);
        onMain(repository.transaction(dao -> {
            dao.moveTransaction(stake.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.APPLIED, now,
                    "taken from balance");
            dao.writeBalance(player, balance.orElseThrow());
            dao.lockOutcome(match.id(), outcome, outcomeId, winner, now);
            dao.audit("system", "LOCKED", match.id(), "outcome " + outcome + " (" + outcomeId + ") drawn after both stakes", now);
            return null;
        })).whenComplete((ignored, error) -> {
            busy.remove(player);
            if (error == null) {
                match.lock(player, stake.playerName(), outcome, outcomeId, now);
                match.transition(MatchState.JOINING, MatchState.LOCKED);
                match.transition(MatchState.LOCKED, MatchState.RESOLVING);
                metrics.success("join");
                debug("locked #" + match.id() + " outcome " + outcome + " (" + outcomeId + ") winner " + match.winnerName());
                markSession(player);
                notifier.matchesChanged();
                long delay = Math.max(0L, notifier.beginReveal(match));
                scheduler.runLater(delay, () -> settle(match));
                result.complete(Outcome.ok("match-joined", matchVars(match), match));
                return;
            }
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.definite()) {
                economy.deposit(player, match.stakeCents());
                releaseJoin(match, stake, "apply rolled back: " + storage.getMessage());
                result.complete(storageOutcome(storage));
            } else {
                holdForRecovery(match, "opponent stake taken in memory; commit outcome unknown (" + storage.getMessage() + ")");
                result.complete(Outcome.fail("held-for-review", matchVars(match)));
            }
        });
    }

    /** Voids a join whose stake was never taken (or was given back) and lists the match again. */
    private void releaseJoin(CoinFlipMatch match, CoinFlipTransaction stake, String note) {
        long now = clock.getAsLong();
        onMain(repository.transaction(dao -> {
            voidIfIntent(dao, stake.id(), now, note);
            try {
                dao.releaseOpponent(match.id());
            } catch (CoinFlipStorageException conflict) {
                // the reservation was never stored
            }
            return null;
        })).whenComplete((ignored, error) -> {
            if (error == null) {
                match.clearPendingOpponent();
                match.transition(MatchState.JOINING, MatchState.WAITING);
                notifier.matchesChanged();
            } else {
                holdForRecovery(match, "could not release a failed join (" + error.getMessage() + ")");
            }
        });
    }

    // ── Settle ───────────────────────────────────────────────────────────────────

    /** Pays the winner of a LOCKED match. Safe to call more than once; only one settlement can run. */
    public void settle(CoinFlipMatch match) {
        settle(match, 0);
    }

    private void settle(CoinFlipMatch match, int attempt) {
        if (!settling.add(match.id())) {
            return;
        }
        boolean claimed = match.transition(MatchState.RESOLVING, MatchState.SETTLING)
                || match.transition(MatchState.LOCKED, MatchState.SETTLING);
        if (!claimed) {
            settling.remove(match.id());
            return;
        }
        UUID winner = match.winnerId();
        long now = clock.getAsLong();
        boolean online = economy.isOnline(winner);
        CoinFlipTransaction payout = newTransaction(match, winner, match.winnerName(), CoinFlipTransaction.Kind.PAYOUT,
                match.payoutCents(), online ? CoinFlipTransaction.State.INTENT : CoinFlipTransaction.State.PENDING_CLAIM,
                "winner payout", now);
        CoinFlipConfig current = config.get();
        if (!online) {
            onMain(repository.transaction(dao -> {
                dao.insertTransaction(payout);
                return Settlement.finish(dao, match, now, zone, current.achievementsEnabled());
            })).whenComplete((unlocked, error) -> finishSettlement(match, unlocked, error, false, attempt));
            return;
        }
        onMain(repository.transaction(dao -> {
            dao.insertTransaction(payout);
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                CoinFlipStorageException storage = CoinFlipStorageException.from(error);
                settling.remove(match.id());
                if (storage.definite()) {
                    match.transition(MatchState.SETTLING, MatchState.LOCKED);
                    retrySettlementLater(match, attempt, storage.getMessage());
                } else {
                    holdForRecovery(match, "payout intent commit uncertain (" + storage.getMessage() + ")");
                }
                return;
            }
            payWinner(match, payout, current, attempt);
        });
    }

    private void payWinner(CoinFlipMatch match, CoinFlipTransaction payout, CoinFlipConfig current, int attempt) {
        UUID winner = payout.playerId();
        long now = clock.getAsLong();
        if (!economy.isOnline(winner) || !economy.deposit(winner, payout.amountCents())) {
            // Left in the meantime: the payout becomes a claim, nothing was credited.
            onMain(repository.transaction(dao -> {
                dao.moveTransaction(payout.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.PENDING_CLAIM,
                        now, "winner offline at payout");
                return Settlement.finish(dao, match, now, zone, current.achievementsEnabled());
            })).whenComplete((unlocked, error) -> finishSettlement(match, unlocked, error, false, attempt));
            return;
        }
        applyCredit(payout, () -> {
            Optional<EconomyGateway.StoredBalance> balance = economy.storedBalance(winner);
            long at = clock.getAsLong();
            return repository.transaction(dao -> {
                dao.moveTransaction(payout.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.APPLIED, at,
                        "added to balance");
                dao.writeBalance(winner, balance.orElseThrow());
                return Settlement.finish(dao, match, at, zone, current.achievementsEnabled());
            });
        }, 0, (unlocked, error) -> finishSettlement(match, unlocked, error, true, attempt));
    }

    private void finishSettlement(CoinFlipMatch match, Map<UUID, List<Achievement>> unlocked, Throwable error,
                                  boolean winnerPaidNow, int attempt) {
        settling.remove(match.id());
        if (error != null) {
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.definite() && !winnerPaidNow) {
                match.transition(MatchState.SETTLING, MatchState.LOCKED);
                retrySettlementLater(match, attempt, storage.getMessage());
            } else {
                holdForRecovery(match, (winnerPaidNow ? "payout added in memory but not recorded" : "settlement commit uncertain")
                        + " (" + storage.getMessage() + ")");
            }
            return;
        }
        match.markEnded(clock.getAsLong(), "completed");
        match.transition(MatchState.SETTLING, MatchState.COMPLETED);
        active.remove(match.id());
        metrics.success("settle");
        debug("settled #" + match.id() + " payout " + StakeMath.format(match.payoutCents()) + (winnerPaidNow ? " paid" : " as claim"));
        notifier.matchCompleted(match, winnerPaidNow);
        notifier.matchesChanged();
        if (unlocked != null) {
            unlocked.forEach(notifier::achievementsUnlocked);
        }
        remindIfLongSession(match.creatorId());
        remindIfLongSession(match.opponentId());
    }

    private void retrySettlementLater(CoinFlipMatch match, int attempt, String reason) {
        metrics.failure("settle");
        if (attempt + 1 >= APPLY_RETRIES) {
            notifier.staffAlert("Match #" + match.id() + " could not be paid out after " + APPLY_RETRIES
                    + " tries (" + reason + "). It stays LOCKED with its stored result; /coinflipadmin recover retries.");
            return;
        }
        scheduler.runLater(100L * (attempt + 1), () -> settle(match, attempt + 1));
    }

    /**
     * Runs the apply step of a credit. A credit already added in memory cannot always be taken back (the player
     * may have spent it), so a rolled-back apply is retried with a fresh balance; after the retries the match is
     * held for staff with the journal row still at INTENT.
     */
    private <T> void applyCredit(CoinFlipTransaction credit, Supplier<CompletableFuture<T>> apply, int attempt,
                                 java.util.function.BiConsumer<T, Throwable> done) {
        onMain(apply.get()).whenComplete((value, error) -> {
            if (error == null) {
                done.accept(value, null);
                return;
            }
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.definite() && storage.reason() != CoinFlipStorageException.Reason.CONFLICT && attempt + 1 < APPLY_RETRIES) {
                logger.warning("[CoinFlip] Recording credit " + credit.id() + " failed (" + storage.getMessage() + "), retrying.");
                scheduler.runLater(20L, () -> applyCredit(credit, apply, attempt + 1, done));
                return;
            }
            done.accept(null, error);
        });
    }

    // ── Cancel, decline, expire ──────────────────────────────────────────────────

    public CompletableFuture<Outcome> cancel(UUID player, String matchId) {
        CoinFlipMatch match = active.get(normalizeId(matchId));
        if (match == null) {
            return done(Outcome.fail("match-not-found", vars("id", normalizeId(matchId))));
        }
        if (!match.creatorId().equals(player)) {
            return done(Outcome.fail("cancel-not-allowed", matchVars(match)));
        }
        return refund(match, MatchState.CANCELLED, "cancelled by creator", player.toString(), false);
    }

    public CompletableFuture<Outcome> decline(UUID player, String matchId) {
        CoinFlipMatch match = active.get(normalizeId(matchId));
        if (match == null || !match.isPrivate()) {
            return done(Outcome.fail("match-not-found", vars("id", normalizeId(matchId))));
        }
        if (!player.equals(match.inviteeId())) {
            return done(Outcome.fail("not-your-challenge", matchVars(match)));
        }
        return refund(match, MatchState.CANCELLED, "declined by " + match.inviteeName(), player.toString(), false)
                .thenApply(outcome -> {
                    if (outcome.success()) {
                        notifier.send(match.creatorId(), "challenge-declined", matchVars(match));
                        return Outcome.ok("challenge-declined-self", matchVars(match), match);
                    }
                    return outcome;
                });
    }

    /** Staff cancellation of a waiting match; a reason is mandatory and audited. */
    public CompletableFuture<Outcome> adminCancel(String actor, String matchId, String reason) {
        CoinFlipMatch match = active.get(normalizeId(matchId));
        if (match == null) {
            return done(Outcome.fail("match-not-found", vars("id", normalizeId(matchId))));
        }
        if (reason == null || reason.isBlank()) {
            return done(Outcome.fail("admin-reason-required"));
        }
        if (match.state() != MatchState.WAITING) {
            return done(Outcome.fail("admin-cancel-locked", matchVars(match)));
        }
        return refund(match, MatchState.CANCELLED, "staff: " + reason, actor, true);
    }

    /** Called every second: expires waiting matches past their time, a bounded number per call. */
    public void sweepExpired() {
        long now = clock.getAsLong();
        int handled = 0;
        for (CoinFlipMatch match : List.copyOf(active.values())) {
            if (handled >= MAX_SWEEP_PER_TICK) {
                break;
            }
            if (match.state() == MatchState.WAITING && match.isExpired(now)) {
                handled++;
                refund(match, MatchState.EXPIRED, match.isPrivate() ? "challenge expired" : "expired", "system", false);
            }
        }
    }

    private CompletableFuture<Outcome> refund(CoinFlipMatch match, MatchState endState, String reason, String actor,
                                              boolean audit) {
        if (!match.transition(MatchState.WAITING, MatchState.CANCELLING)) {
            return done(Outcome.fail("cancel-not-allowed", matchVars(match)));
        }
        UUID creator = match.creatorId();
        long now = clock.getAsLong();
        boolean online = economy.isOnline(creator);
        CoinFlipTransaction refund = newTransaction(match, creator, match.creatorName(), CoinFlipTransaction.Kind.REFUND,
                match.stakeCents(), online ? CoinFlipTransaction.State.INTENT : CoinFlipTransaction.State.PENDING_CLAIM,
                reason, now);
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        if (!online) {
            onMain(repository.transaction(dao -> {
                dao.insertTransaction(refund);
                dao.moveMatch(match.id(), MatchState.WAITING, endState, now, reason);
                if (audit) {
                    dao.audit(actor, "CANCELLED", match.id(), reason, now);
                }
                return null;
            })).whenComplete((ignored, error) -> finishRefund(match, endState, reason, error, result));
            return result;
        }
        onMain(repository.transaction(dao -> {
            dao.insertTransaction(refund);
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                CoinFlipStorageException storage = CoinFlipStorageException.from(error);
                if (storage.definite()) {
                    match.transition(MatchState.CANCELLING, MatchState.WAITING);
                    result.complete(storageOutcome(storage));
                } else {
                    holdForRecovery(match, "refund intent commit uncertain (" + storage.getMessage() + ")");
                    result.complete(Outcome.fail("held-for-review", matchVars(match)));
                }
                return;
            }
            if (!economy.isOnline(creator) || !economy.deposit(creator, match.stakeCents())) {
                long at = clock.getAsLong();
                onMain(repository.transaction(dao -> {
                    dao.moveTransaction(refund.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.PENDING_CLAIM,
                            at, "creator offline at refund");
                    dao.moveMatch(match.id(), MatchState.WAITING, endState, at, reason);
                    if (audit) {
                        dao.audit(actor, "CANCELLED", match.id(), reason, at);
                    }
                    return null;
                })).whenComplete((ignored2, error2) -> finishRefund(match, endState, reason, error2, result));
                return;
            }
            applyCredit(refund, () -> {
                Optional<EconomyGateway.StoredBalance> balance = economy.storedBalance(creator);
                long at = clock.getAsLong();
                return repository.transaction(dao -> {
                    dao.moveTransaction(refund.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.APPLIED, at,
                            "returned to balance");
                    dao.writeBalance(creator, balance.orElseThrow());
                    dao.moveMatch(match.id(), MatchState.WAITING, endState, at, reason);
                    if (audit) {
                        dao.audit(actor, "CANCELLED", match.id(), reason, at);
                    }
                    return null;
                });
            }, 0, (ignored2, error2) -> {
                if (error2 != null) {
                    holdForRecovery(match, "refund added in memory but not recorded (" + error2.getMessage() + ")");
                    result.complete(Outcome.fail("held-for-review", matchVars(match)));
                    return;
                }
                finishRefund(match, endState, reason, null, result);
            });
        });
        return result;
    }

    private void finishRefund(CoinFlipMatch match, MatchState endState, String reason, Throwable error,
                              CompletableFuture<Outcome> result) {
        if (error != null) {
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.definite()) {
                match.transition(MatchState.CANCELLING, MatchState.WAITING);
                result.complete(storageOutcome(storage));
            } else {
                holdForRecovery(match, "refund commit uncertain (" + storage.getMessage() + ")");
                result.complete(Outcome.fail("held-for-review", matchVars(match)));
            }
            return;
        }
        match.markEnded(clock.getAsLong(), reason);
        match.transition(MatchState.CANCELLING, endState);
        active.remove(match.id());
        metrics.success(endState == MatchState.EXPIRED ? "expire" : "cancel");
        debug(endState.name().toLowerCase(Locale.ROOT) + " #" + match.id() + " refund " + StakeMath.format(match.stakeCents()));
        notifier.matchesChanged();
        String key = endState == MatchState.EXPIRED ? "match-expired" : "match-cancelled";
        notifier.send(match.creatorId(), key, matchVars(match));
        result.complete(Outcome.ok(key, matchVars(match), match));
    }

    // ── Claims ───────────────────────────────────────────────────────────────────

    /** Pays every credit owed to a player who is now online, one at a time. */
    public void deliverClaims(UUID player) {
        if (!repository.isReady() || !recovered || !deliveringClaims.add(player)) {
            return;
        }
        onMain(repository.transaction(dao -> dao.claimsFor(player))).whenComplete((claims, error) -> {
            if (error != null || claims == null || claims.isEmpty()) {
                deliveringClaims.remove(player);
                return;
            }
            deliverNext(player, new ArrayList<>(claims));
        });
    }

    private void deliverNext(UUID player, List<CoinFlipTransaction> remaining) {
        if (remaining.isEmpty() || !economy.isOnline(player)) {
            deliveringClaims.remove(player);
            return;
        }
        CoinFlipTransaction claim = remaining.removeFirst();
        long now = clock.getAsLong();
        onMain(repository.transaction(dao -> {
            dao.moveTransaction(claim.id(), CoinFlipTransaction.State.PENDING_CLAIM, CoinFlipTransaction.State.INTENT, now,
                    "delivering");
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                CoinFlipStorageException storage = CoinFlipStorageException.from(error);
                if (!storage.definite()) {
                    notifier.staffAlert("Claim " + claim.id() + " for match #" + claim.matchId()
                            + " has an unknown delivery state (" + storage.getMessage() + "). Check /coinflipadmin inspect "
                            + claim.matchId());
                }
                deliveringClaims.remove(player);
                return;
            }
            if (!economy.isOnline(player) || !economy.deposit(player, claim.amountCents())) {
                onMain(repository.transaction(dao -> {
                    dao.moveTransaction(claim.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.PENDING_CLAIM,
                            clock.getAsLong(), "delivery postponed");
                    return null;
                })).whenComplete((ignored2, error2) -> deliveringClaims.remove(player));
                return;
            }
            applyCredit(claim, () -> {
                Optional<EconomyGateway.StoredBalance> balance = economy.storedBalance(player);
                long at = clock.getAsLong();
                return repository.transaction(dao -> {
                    dao.moveTransaction(claim.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.APPLIED, at,
                            "claim delivered");
                    dao.writeBalance(player, balance.orElseThrow());
                    return null;
                });
            }, 0, (ignored2, error2) -> {
                if (error2 != null) {
                    notifier.staffAlert("Claim " + claim.id() + " for match #" + claim.matchId()
                            + " was added in memory but not recorded. Resolve it as applied with /coinflipadmin resolve "
                            + claim.id() + " applied <reason> after checking the balance.");
                    deliveringClaims.remove(player);
                    return;
                }
                metrics.success("claim");
                debug("claim " + claim.id() + " delivered: " + StakeMath.format(claim.amountCents()));
                notifier.send(player, "claim-delivered", vars("amount", StakeMath.format(claim.amountCents()),
                        "id", claim.matchId(), "kind", claim.kind() == CoinFlipTransaction.Kind.PAYOUT ? "winnings" : "refund"));
                deliverNext(player, remaining);
            });
        });
    }

    // ── Recovery and staff tools ─────────────────────────────────────────────────

    /** Result of startup recovery. */
    public record RecoveryReport(int unknownTransactions, int refunded, int settled, int cancelled, int heldForReview) {
    }

    /**
     * Brings storage to a consistent state after a start: unfinished intents become UNKNOWN (their match is held
     * for review), waiting matches are refunded as claims, locked matches are paid out as claims. All of it is
     * database-only, so running it twice changes nothing the second time.
     */
    public CompletableFuture<RecoveryReport> recoverAtStartup() {
        CoinFlipConfig current = config.get();
        long now = clock.getAsLong();
        return onMain(repository.transaction(dao -> {
            int unknown = 0;
            for (CoinFlipTransaction intent : dao.transactionsInState(CoinFlipTransaction.State.INTENT, 10_000)) {
                dao.moveTransaction(intent.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.UNKNOWN, now,
                        "unfinished at startup");
                unknown++;
            }
            int refunded = 0;
            int settled = 0;
            int cancelled = 0;
            int held = 0;
            for (CoinFlipMatch match : dao.loadUnfinishedMatches()) {
                Settlement.Ledger ledger = Settlement.ledger(dao.transactionsForMatch(match.id()));
                if (ledger.anyUnknown()) {
                    if (match.state() != MatchState.RECOVERY_REQUIRED) {
                        dao.moveMatch(match.id(), match.state(), MatchState.RECOVERY_REQUIRED, 0L, "unknown transaction at startup");
                    }
                    held++;
                    continue;
                }
                switch (match.state()) {
                    case CREATING -> {
                        dao.moveMatch(match.id(), MatchState.CREATING, MatchState.CANCELLED, now, "server stopped before the stake was taken");
                        cancelled++;
                    }
                    case WAITING, JOINING -> {
                        if (match.state() == MatchState.JOINING) {
                            dao.releaseOpponent(match.id());
                        }
                        dao.insertTransaction(new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), match.creatorId(),
                                match.creatorName(), CoinFlipTransaction.Kind.REFUND, match.stakeCents(),
                                CoinFlipTransaction.State.PENDING_CLAIM, "server restart", now, now, "recovery"));
                        dao.moveMatch(match.id(), MatchState.WAITING, MatchState.EXPIRED, now, "server restart");
                        refunded++;
                    }
                    case LOCKED -> {
                        dao.insertTransaction(new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), match.winnerId(),
                                match.winnerName(), CoinFlipTransaction.Kind.PAYOUT, match.payoutCents(),
                                CoinFlipTransaction.State.PENDING_CLAIM, "winner payout", now, now, "recovery"));
                        Settlement.finish(dao, match, now, zone, current.achievementsEnabled());
                        settled++;
                    }
                    case RECOVERY_REQUIRED -> {
                        if (Settlement.reconcile(dao, match.id(), now, zone, current.achievementsEnabled(), "startup")) {
                            cancelled++;
                        } else {
                            held++;
                        }
                    }
                    default -> held++;
                }
            }
            dao.audit("system", "RECOVERY", null, "unknown=" + unknown + " refunded=" + refunded + " settled=" + settled
                    + " cancelled=" + cancelled + " held=" + held, now);
            return new RecoveryReport(unknown, refunded, settled, cancelled, held);
        })).thenCompose(report -> onMain(repository.transaction(CoinFlipDao::loadUnfinishedMatches)).thenApply(left -> {
            for (CoinFlipMatch match : left) {
                active.put(match.id(), match);
            }
            recovered = true;
            if (report.heldForReview() > 0) {
                notifier.staffAlert(report.heldForReview() + " CoinFlip match(es) need review: /coinflipadmin status");
            }
            return report;
        }));
    }

    /**
     * Staff decision on an UNKNOWN (or stuck INTENT) transaction. {@code applied} means the balance did change.
     * Afterwards the match is reconciled from the journal if nothing else is unresolved.
     */
    public CompletableFuture<Outcome> resolve(String actor, String transactionId, boolean applied, String reason) {
        if (reason == null || reason.isBlank()) {
            return done(Outcome.fail("admin-reason-required"));
        }
        CoinFlipConfig current = config.get();
        long now = clock.getAsLong();
        // Matches whose money movement is still running here; their INTENT rows are not stuck, just in progress.
        Set<String> inFlightIds = new HashSet<>();
        for (CoinFlipMatch match : active.values()) {
            if (match.state() != MatchState.RECOVERY_REQUIRED) {
                inFlightIds.add(match.id());
            }
        }
        return onMain(repository.transaction(dao -> {
            CoinFlipTransaction tx = dao.loadTransaction(transactionId).orElseThrow(() -> new CoinFlipStorageException(
                    CoinFlipStorageException.Reason.REFUSED, "admin-tx-not-found"));
            CoinFlipMatch stored = dao.loadMatch(tx.matchId()).orElseThrow();
            boolean inFlight = inFlightIds.contains(stored.id());
            if (tx.state() != CoinFlipTransaction.State.UNKNOWN
                    && !(tx.state() == CoinFlipTransaction.State.INTENT && !inFlight)) {
                throw new CoinFlipStorageException(CoinFlipStorageException.Reason.REFUSED, "admin-tx-not-unknown");
            }
            CoinFlipTransaction.State target = applied ? CoinFlipTransaction.State.APPLIED
                    : tx.kind() == CoinFlipTransaction.Kind.STAKE ? CoinFlipTransaction.State.VOID
                    : CoinFlipTransaction.State.PENDING_CLAIM;
            dao.moveTransaction(tx.id(), tx.state(), target, now, "resolved by " + actor + ": " + reason);
            dao.audit(actor, "RESOLVED", tx.matchId(), tx.id() + " " + tx.kind() + " -> " + target + ": " + reason, now);
            if (stored.state() == MatchState.COMPLETED || stored.state() == MatchState.CANCELLED
                    || stored.state() == MatchState.EXPIRED) {
                return stored.id();
            }
            if (stored.state() != MatchState.RECOVERY_REQUIRED) {
                dao.moveMatch(stored.id(), stored.state(), MatchState.RECOVERY_REQUIRED, 0L, "staff resolution");
            }
            Settlement.reconcile(dao, stored.id(), now, zone, current.achievementsEnabled(), actor);
            return stored.id();
        })).thenCompose(this::reloadAfterStaffAction).exceptionally(error -> {
            CoinFlipStorageException storage = CoinFlipStorageException.from(error);
            if (storage.reason() == CoinFlipStorageException.Reason.REFUSED) {
                return Outcome.fail(storage.getMessage(), vars("id", transactionId));
            }
            return storageOutcome(storage);
        });
    }

    /** Retries stuck settlements and reconciles held matches whose transactions are all resolved. */
    public CompletableFuture<Integer> recoverNow(String actor) {
        int retried = 0;
        for (CoinFlipMatch match : List.copyOf(active.values())) {
            if (match.state() == MatchState.LOCKED && !settling.contains(match.id())) {
                settle(match);
                retried++;
            }
        }
        List<String> held = active.values().stream().filter(match -> match.state() == MatchState.RECOVERY_REQUIRED)
                .map(CoinFlipMatch::id).toList();
        if (held.isEmpty()) {
            return CompletableFuture.completedFuture(retried);
        }
        CoinFlipConfig current = config.get();
        long now = clock.getAsLong();
        int base = retried;
        return onMain(repository.transaction(dao -> {
            int done = 0;
            for (String id : held) {
                CoinFlipMatch stored = dao.loadMatch(id).orElse(null);
                if (stored == null) {
                    continue;
                }
                if (Settlement.ledger(dao.transactionsForMatch(id)).anyUnknown()) {
                    for (CoinFlipTransaction tx : dao.transactionsForMatch(id)) {
                        if (tx.state() == CoinFlipTransaction.State.INTENT) {
                            dao.moveTransaction(tx.id(), CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.UNKNOWN,
                                    now, "marked unknown by " + actor);
                        }
                    }
                    if (stored.state() != MatchState.RECOVERY_REQUIRED) {
                        dao.moveMatch(id, stored.state(), MatchState.RECOVERY_REQUIRED, 0L, "held for review");
                    }
                    continue;
                }
                if (stored.state() != MatchState.RECOVERY_REQUIRED && !stored.state().isFinal()) {
                    dao.moveMatch(id, stored.state(), MatchState.RECOVERY_REQUIRED, 0L, "held for review");
                }
                if (Settlement.reconcile(dao, id, now, zone, current.achievementsEnabled(), actor)) {
                    done++;
                }
            }
            dao.audit(actor, "RECOVER", null, "reconciled " + done + " held match(es)", now);
            return done;
        })).thenCompose(done -> {
            CompletableFuture<Integer> chain = CompletableFuture.completedFuture(base + done);
            for (String id : held) {
                chain = chain.thenCompose(total -> reloadAfterStaffAction(id).thenApply(ignored -> total));
            }
            return chain;
        });
    }

    /** Re-reads one match after staff work: final matches leave memory, others are refreshed. */
    private CompletableFuture<Outcome> reloadAfterStaffAction(String matchId) {
        return onMain(repository.transaction(dao -> dao.loadMatch(matchId))).thenApply(stored -> {
            if (stored.isEmpty()) {
                active.remove(matchId);
                return Outcome.ok("admin-resolved", vars("id", matchId), null);
            }
            CoinFlipMatch match = stored.get();
            if (match.state().isFinal()) {
                active.remove(matchId);
                for (UUID participant : new UUID[]{match.creatorId(), match.opponentId()}) {
                    if (participant != null) {
                        deliverClaims(participant);
                    }
                }
            } else {
                active.put(matchId, match);
            }
            notifier.matchesChanged();
            return Outcome.ok("admin-resolved", matchVars(match), match);
        });
    }

    /** Ledger check of recent finished matches: every stake taken is paid out, refunded or held as a fee. */
    public CompletableFuture<List<String>> reconcileReport(int limit) {
        return onMain(repository.transaction(dao -> {
            List<String> problems = new ArrayList<>();
            for (CoinFlipTransaction unknown : dao.transactionsInState(CoinFlipTransaction.State.UNKNOWN, 100)) {
                problems.add("UNKNOWN " + unknown.kind() + " " + unknown.id() + " match #" + unknown.matchId() + " "
                        + StakeMath.format(unknown.amountCents()) + " for " + unknown.playerName());
            }
            List<CoinFlipDao.AuditRow> recent = dao.auditLog(null, Math.max(1, limit));
            Set<String> checked = new HashSet<>();
            for (CoinFlipDao.AuditRow row : recent) {
                if (row.matchId() == null || !checked.add(row.matchId())) {
                    continue;
                }
                Optional<CoinFlipMatch> stored = dao.loadMatch(row.matchId());
                if (stored.isEmpty()) {
                    continue;
                }
                CoinFlipMatch match = stored.get();
                Settlement.Ledger ledger = Settlement.ledger(dao.transactionsForMatch(match.id()));
                if (match.state() == MatchState.COMPLETED) {
                    long expectedStakes = match.potCents();
                    if (ledger.stakesApplied() != expectedStakes || ledger.credits() != match.payoutCents()) {
                        problems.add("#" + match.id() + " completed: stakes " + StakeMath.format(ledger.stakesApplied())
                                + " (expected " + StakeMath.format(expectedStakes) + "), credits "
                                + StakeMath.format(ledger.credits()) + " (expected " + StakeMath.format(match.payoutCents()) + ")");
                    }
                } else if (match.state() == MatchState.CANCELLED || match.state() == MatchState.EXPIRED) {
                    if (ledger.credits() != ledger.stakesApplied()) {
                        problems.add("#" + match.id() + " " + match.state().name().toLowerCase(Locale.ROOT) + ": stakes "
                                + StakeMath.format(ledger.stakesApplied()) + ", refunds " + StakeMath.format(ledger.credits()));
                    }
                }
            }
            return problems;
        }));
    }

    public CompletableFuture<Outcome> resetStats(String actor, UUID player, String playerName, String reason) {
        if (reason == null || reason.isBlank()) {
            return done(Outcome.fail("admin-reason-required"));
        }
        long now = clock.getAsLong();
        return onMain(repository.transaction(dao -> {
            dao.resetStats(player);
            dao.audit(actor, "STATS_RESET", null, playerName + " (" + player + "): " + reason, now);
            return null;
        })).thenApply(ignored -> Outcome.ok("admin-stats-reset", vars("player", playerName), null))
                .exceptionally(error -> storageOutcome(CoinFlipStorageException.from(error)));
    }

    public void setWageringPaused(boolean paused, String actor) {
        this.wageringPaused = paused;
        long now = clock.getAsLong();
        repository.transaction(dao -> {
            dao.audit(actor, paused ? "DISABLED" : "ENABLED", null, "wagering " + (paused ? "paused" : "resumed"), now);
            return null;
        });
    }

    // ── Practice ─────────────────────────────────────────────────────────────────

    /** A practice flip: same randomness, no currency, no wagering record. Only a practice counter is kept. */
    public CoinSide practiceFlip(UUID player, String name) {
        CoinSide result = random.flip();
        if (repository.isReady()) {
            long now = clock.getAsLong();
            boolean achievements = config.get().achievementsEnabled();
            onMain(repository.transaction(dao -> {
                CoinFlipStats after = dao.loadStats(player, name).afterPractice();
                dao.saveStats(after);
                List<Achievement> fresh = new ArrayList<>();
                if (achievements) {
                    for (Achievement achievement : Achievement.reachedBy(after)) {
                        if (dao.unlock(player, achievement, now)) {
                            fresh.add(achievement);
                        }
                    }
                }
                return fresh;
            })).thenAccept(fresh -> {
                if (!fresh.isEmpty()) {
                    notifier.achievementsUnlocked(player, fresh);
                }
            });
        }
        return result;
    }

    // ── Eligibility ──────────────────────────────────────────────────────────────

    private Optional<Outcome> checkCanWager(UUID player, long stakeCents, long now, CoinFlipConfig current) {
        if (!current.enabled()) {
            return Optional.of(Outcome.fail("disabled"));
        }
        if (current.maintenance() || wageringPaused) {
            return Optional.of(Outcome.fail("maintenance"));
        }
        if (current.wageringBlocked()) {
            return Optional.of(Outcome.fail("wagering-blocked"));
        }
        if (!repository.isReady() || !recovered) {
            return Optional.of(Outcome.fail("storage-unavailable"));
        }
        if (busy.contains(player)) {
            return Optional.of(Outcome.fail("in-progress"));
        }
        if (!preferences.containsKey(player)) {
            loadPreferences(player);
            return Optional.of(Outcome.fail("loading"));
        }
        CoinFlipPreferences prefs = preferences.get(player);
        if (prefs.selfExcluded(now)) {
            return Optional.of(Outcome.fail("self-excluded", vars("time", formatTime(prefs.selfExcludedUntilMillis()))));
        }
        if (current.minPlaytimeMinutes() > 0 && playtimeMinutes.applyAsLong(player) < current.minPlaytimeMinutes()) {
            return Optional.of(Outcome.fail("playtime-required", vars("minutes", Long.toString(current.minPlaytimeMinutes()))));
        }
        if (stakeCents <= 0L) {
            return Optional.of(Outcome.fail("invalid-amount", vars("reason", "zero")));
        }
        if (!current.allowDecimals() && stakeCents % 100L != 0L) {
            return Optional.of(Outcome.fail("whole-numbers-only"));
        }
        if (stakeCents < current.minStakeCents()) {
            return Optional.of(Outcome.fail("stake-below-min", vars("min", StakeMath.format(current.minStakeCents()))));
        }
        if (stakeCents > current.maxStakeCents()) {
            return Optional.of(Outcome.fail("stake-above-max", vars("max", StakeMath.format(current.maxStakeCents()))));
        }
        long balance = economy.balanceCents(player);
        if (balance < stakeCents) {
            return Optional.of(Outcome.fail("insufficient-funds", vars("amount", StakeMath.format(stakeCents),
                    "balance", StakeMath.format(balance))));
        }
        return Optional.empty();
    }

    /** Daily caps, checked inside the same transaction that records the stake intent, so they cannot be raced. */
    private static void checkDailyLimits(CoinFlipDao dao, UUID player, long stake, long dayStart, boolean creating,
                                         CoinFlipConfig current) throws java.sql.SQLException {
        if (current.dailyWagerCapCents() > 0L && dao.stakedSince(player, dayStart) + stake > current.dailyWagerCapCents()) {
            throw new CoinFlipStorageException(CoinFlipStorageException.Reason.REFUSED, "daily-wager-cap");
        }
        if (current.dailyNetLossCapCents() > 0L) {
            long net = 0L;
            for (CoinFlipMatch match : dao.completedSince(player, dayStart)) {
                net += player.equals(match.winnerId()) ? match.payoutCents() - match.stakeCents() : -match.stakeCents();
            }
            if (-net >= current.dailyNetLossCapCents()) {
                throw new CoinFlipStorageException(CoinFlipStorageException.Reason.REFUSED, "daily-loss-cap");
            }
        }
        if (creating && current.dailyMatchCap() > 0 && dao.countCreatedSince(player, dayStart) >= current.dailyMatchCap()) {
            throw new CoinFlipStorageException(CoinFlipStorageException.Reason.REFUSED, "daily-match-cap");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Voids an intent that never changed a balance, and cancels the match row if it is still CREATING. */
    private void abandon(CoinFlipMatch match, CoinFlipTransaction intent, MatchState expected, String note) {
        long now = clock.getAsLong();
        repository.transaction(dao -> {
            voidIfIntent(dao, intent.id(), now, note);
            try {
                dao.moveMatch(match.id(), expected, MatchState.CANCELLED, now, note);
            } catch (CoinFlipStorageException conflict) {
                // the match row was never stored
            }
            return null;
        }).exceptionally(error -> {
            logger.log(Level.WARNING, "[CoinFlip] Could not void intent " + intent.id()
                    + "; it is reviewed at next start", error);
            return null;
        });
    }

    private static void voidIfIntent(CoinFlipDao dao, String transactionId, long now, String note) throws java.sql.SQLException {
        try {
            dao.moveTransaction(transactionId, CoinFlipTransaction.State.INTENT, CoinFlipTransaction.State.VOID, now, note);
        } catch (CoinFlipStorageException conflict) {
            // never stored, or already settled
        }
    }

    private void debug(String message) {
        if (config.get().debug()) {
            logger.info("[CoinFlip debug] " + message);
        }
    }

    private void holdForRecovery(CoinFlipMatch match, String detail) {
        MatchState state = match.state();
        if (state != MatchState.RECOVERY_REQUIRED && !state.isFinal()) {
            match.transition(state, MatchState.RECOVERY_REQUIRED);
        }
        metrics.failure("recovery-required");
        logger.severe("[CoinFlip] Match #" + match.id() + " held for review: " + detail);
        notifier.staffAlert("Match #" + match.id() + " needs review: " + detail + ". /coinflipadmin inspect " + match.id());
        notifier.matchesChanged();
    }

    private Outcome storageOutcome(CoinFlipStorageException storage) {
        return switch (storage.reason()) {
            case BUSY -> Outcome.fail("storage-busy");
            case REFUSED -> {
                CoinFlipConfig current = config.get();
                String key = storage.getMessage();
                yield Outcome.fail(key, vars(
                        "cap", StakeMath.format(switch (key) {
                            case "daily-wager-cap" -> current.dailyWagerCapCents();
                            case "daily-loss-cap" -> current.dailyNetLossCapCents();
                            default -> current.dailyMatchCap() * 100L;
                        })));
            }
            case CONFLICT -> Outcome.fail("match-changed");
            default -> Outcome.fail("storage-unavailable");
        };
    }

    private CoinFlipTransaction newTransaction(CoinFlipMatch match, UUID player, String name, CoinFlipTransaction.Kind kind,
                                               long amount, CoinFlipTransaction.State state, String reason, long now) {
        return new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), player, name, kind, amount, state, reason,
                now, now, null);
    }

    private String newMatchId() {
        StringBuilder id = new StringBuilder(8);
        do {
            id.setLength(0);
            String source = random.newOutcomeId();
            for (int i = 0; i < 8; i++) {
                int value = Character.digit(source.charAt(i * 2), 16) * 16 + Character.digit(source.charAt(i * 2 + 1), 16);
                id.append(ID_ALPHABET.charAt(value % ID_ALPHABET.length()));
            }
        } while (active.containsKey(id.toString()));
        return id.toString();
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.replaceAll("[^A-Z0-9]", "");
    }

    public Map<String, String> matchVars(CoinFlipMatch match) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("id", match.id());
        vars.put("creator", match.creatorName());
        vars.put("opponent", match.opponentName() == null ? "-" : match.opponentName());
        vars.put("target", match.inviteeName() == null ? "-" : match.inviteeName());
        vars.put("stake", StakeMath.format(match.stakeCents()));
        vars.put("pot", StakeMath.format(match.potCents()));
        vars.put("fee", StakeMath.format(match.feeCents()));
        vars.put("payout", StakeMath.format(match.payoutCents()));
        vars.put("net", StakeMath.format(StakeMath.winnerNet(match.stakeCents(), match.feeBasisPoints())));
        vars.put("side", match.creatorSide().displayName());
        vars.put("opponentSide", match.creatorSide().opposite().displayName());
        vars.put("outcome", match.outcome() == null ? "-" : match.outcome().displayName());
        vars.put("winner", match.winnerName() == null ? "-" : match.winnerName());
        vars.put("state", match.state().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        vars.put("expiry", seconds(Math.max(0L, match.expiresAtMillis() - clock.getAsLong())) + "s");
        return vars;
    }

    private void markSession(UUID player) {
        sessionStart.putIfAbsent(player, clock.getAsLong());
    }

    private void remindIfLongSession(UUID player) {
        long interval = config.get().sessionReminderMillis();
        if (interval <= 0L || player == null) {
            return;
        }
        long now = clock.getAsLong();
        Long started = sessionStart.get(player);
        if (started == null || now - started < interval) {
            return;
        }
        long last = lastReminder.getOrDefault(player, started);
        if (now - last >= interval) {
            lastReminder.put(player, now);
            notifier.send(player, "session-reminder", vars("minutes", Long.toString((now - started) / 60_000L)));
        }
    }

    private String formatTime(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    private <T> CompletableFuture<T> onMain(CompletableFuture<T> future) {
        CompletableFuture<T> hopped = new CompletableFuture<>();
        future.whenComplete((value, error) -> mainThread.execute(() -> {
            if (error != null) {
                hopped.completeExceptionally(error);
            } else {
                hopped.complete(value);
            }
        }));
        return hopped;
    }

    private static CompletableFuture<Outcome> done(Outcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }

    private static String seconds(long millis) {
        return Long.toString(Math.max(1L, (millis + 999L) / 1_000L));
    }

    public static Map<String, String> vars(String... pairs) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            vars.put(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return vars;
    }
}

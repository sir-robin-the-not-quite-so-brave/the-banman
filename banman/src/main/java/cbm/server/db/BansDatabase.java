package cbm.server.db;

import cbm.server.Bot;
import cbm.server.SteamID;
import cbm.server.model.Ban;
import cbm.server.model.OfflineBan;
import jetbrains.exodus.backup.BackupBean;
import jetbrains.exodus.bindings.BindingUtils;
import jetbrains.exodus.bindings.ComparableBinding;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.entitystore.StoreTransaction;
import jetbrains.exodus.util.CompressBackupUtil;
import jetbrains.exodus.util.LightOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toMap;
import static jetbrains.exodus.core.crypto.MessageDigestUtil.MD5;

public class BansDatabase implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CURRENT_BAN = "CurrentBan";
    private static final String OFFLINE_BAN = "OfflineBan";
    private static final String LOG_ENTRY = "LogEntry";
    private static final String UNIQUE_BAN = "UniqueBan";
    private static final ComparableBinding INSTANT_BINDING = new ComparableBinding() {
        @SuppressWarnings("rawtypes")
        @Override
        public Comparable readObject(@NotNull ByteArrayInputStream stream) {
            final long epochSecond = BindingUtils.readLong(stream);
            return Instant.ofEpochSecond(epochSecond);
        }

        @Override
        public void writeObject(@NotNull LightOutputStream output, @NotNull Comparable object) {
            final Instant instant = (Instant) object;
            final long epochSecond = instant.getEpochSecond();
            output.writeUnsignedLong(epochSecond ^ 0x8000000000000000L);
        }
    };
    private static final ComparableBinding DURATION_BINDING = new ComparableBinding() {
        @SuppressWarnings("rawtypes")
        @Override
        public Comparable readObject(@NotNull ByteArrayInputStream stream) {
            final long seconds = BindingUtils.readLong(stream);
            return Duration.ofSeconds(seconds);
        }

        @Override
        public void writeObject(@NotNull LightOutputStream output, @NotNull Comparable object) {
            final Duration duration = (Duration) object;
            final long seconds = duration.toSeconds();
            output.writeUnsignedLong(seconds ^ 0x8000000000000000L);
        }
    };

    private static final BiConsumer<PersistentEntityStore, StoreTransaction> REGISTRAR = (store, txn) -> {
        store.registerCustomPropertyType(txn, Instant.class, INSTANT_BINDING);
        store.registerCustomPropertyType(txn, Duration.class, DURATION_BINDING);
    };

    private final PersistentEntityStore entityStore;
    private final SearchIndex searchIndex;

    public BansDatabase(String dir) throws IOException {
        this.entityStore = new CustomTypesPersistentEntityStore(PersistentEntityStores.newInstance(dir), REGISTRAR);
        final Path lucene = Path.of(dir, "lucene");
        final boolean isNew = Files.notExists(lucene);
        this.searchIndex = new SearchIndex(lucene);
        if (isNew) {
            LOGGER.info("Indexing all existing bans ...");
            indexBans();
        }
    }

    public Stats storeBans(Instant timestamp, Stream<Ban> bans) {
        return storeBans(timestamp, bans, false);
    }

    public Stats storeBans(Instant timestamp, Stream<Ban> bans, boolean historicBans) {
        final Map<String, Ban> banMap =
                bans.collect(toMap(Ban::getId,
                                   Function.identity(),
                                   (u, v) -> {
                                       if (Objects.equals(u, v))
                                           LOGGER.info("Duplicate but equal entries: {} and {}", u, v);
                                       else
                                           LOGGER.warn("Duplicate bans: {} and {}", u, v);

                                       return v.getBannedUntil().isAfter(u.getBannedUntil()) ? v : u;
                                   }));

        return entityStore.computeInTransaction(txn -> {
            final Map<String, Entity> currentBans = new HashMap<>();
            final AtomicInteger added = new AtomicInteger();
            final AtomicInteger removed = new AtomicInteger();

            for (Entity banned : txn.getAll(CURRENT_BAN)) {
                final String id = getProperty(banned, "player-id");
                if (banMap.containsKey(id)) {
                    currentBans.put(id, banned);
                } else {
                    final Entity removeLog = txn.newEntity(LOG_ENTRY);
                    setProperty(removeLog, "detected-at", timestamp);
                    setProperty(removeLog, "action", "remove");
                    saveBan(asBan(banned), removeLog);

                    removed.incrementAndGet();

                    banned.delete();
                }
            }

            final Set<UniqueBan> addedBans = new HashSet<>();
            for (Ban ban : banMap.values()) {
                final Entity entity = currentBans.get(ban.getId());
                if (isSameBan(ban, entity))
                    continue;

                if (entity != null) {
                    final Entity removeLog = txn.newEntity(LOG_ENTRY);
                    setProperty(removeLog, "detected-at", timestamp);
                    setProperty(removeLog, "action", "remove");
                    saveBan(asBan(entity), removeLog);

                    removed.incrementAndGet();

                    entity.delete();
                    currentBans.remove(ban.getId());
                }

                final Entity log = txn.newEntity(LOG_ENTRY);
                setProperty(log, "detected-at", timestamp);
                setProperty(log, "action", "add");
                saveBan(ban, log);

                added.incrementAndGet();

                saveBan(ban, txn.newEntity(CURRENT_BAN));
                addedBans.add(new UniqueBan(ban));
            }

            // Index the added bans
            index(txn, addedBans);

            setTimestamp(txn, timestamp);

            if (!historicBans) {
                final Set<String> offlineBannedIDs = new HashSet<>();

                // Remove the applied offline bans
                final EntityIterable entities = txn.getAll(OFFLINE_BAN);
                for (Entity entity : entities) {
                    final OfflineBan offlineBan = asOfflineBan(entity);
                    final Ban ban = banMap.get(offlineBan.getId());

                    if (ban != null && !ban.isNetIDBan() && compare(ban.getDuration(), offlineBan.getDuration()) >= 0)
                        entity.delete();
                    else
                        offlineBannedIDs.add(offlineBan.getId());
                }

                // Add automatic offline bans for broken NetID bans
                banMap.values().stream()
                      .filter(Ban::isNetIDBan)
                      .filter(b -> !offlineBannedIDs.contains(b.getId()))
                      .map(this::convertToOffline)
                      .forEach(offlineBan -> saveOfflineBan(offlineBan, txn.newEntity(OFFLINE_BAN)));
            }

            return new Stats() {
                @Override
                public int numAdded() {
                    return added.get();
                }

                @Override
                public int numRemoved() {
                    return removed.get();
                }
            };
        });
    }

    private void index(StoreTransaction txn, Set<UniqueBan> addedBans) {
        final Map<String, Ban> bans =
                addedBans.stream()
                         .filter(uniqueBan -> !exists(txn, uniqueBan))
                         .collect(toMap(uniqueBan -> saveUniqueBan(uniqueBan,
                                                                   txn.newEntity(UNIQUE_BAN)).getId().toString(),
                                        UniqueBan::getBan));
        try {
            searchIndex.index(bans);
        } catch (IOException e) {
            LOGGER.warn("Failed to index bans", e);
        }
    }

    private boolean exists(StoreTransaction txn, UniqueBan uniqueBan) {
        final EntityIterable entities = txn.find(UNIQUE_BAN, "ban-id", uniqueBan.id);
        return !entities.isEmpty();
    }

    private OfflineBan convertToOffline(Ban ban) {
        final String playerName = Bot.resolveSteamID(ban.getId())
                                     .flatMap(Bot::getPlayerName)
                                     .block();

        return new OfflineBan.Builder()
                .setId(ban.getId())
                .setEnactedTime(Instant.now())
                .setDuration(Duration.ofDays(7))
                .setPlayerName(playerName)
                .setReason("Converted from broken NetID ban.")
                .build();
    }

    private int compare(Duration duration1, Duration duration2) {
        final boolean perma1 = duration1.isZero() || duration1.isNegative();
        final boolean perma2 = duration2.isZero() || duration2.isNegative();

        if (perma1 && perma2)
            return 0;
        if (perma1)
            return 1;
        if (perma2)
            return -1;
        return duration1.compareTo(duration2);
    }

    public boolean addOfflineBanSync(OfflineBan offlineBan) {
        return entityStore.computeInTransaction(txn -> {
            final EntityIterable entities = txn.find(OFFLINE_BAN, "player-id", offlineBan.getId());
            if (!entities.isEmpty())
                return false;

            saveOfflineBan(offlineBan, txn.newEntity(OFFLINE_BAN));
            return true;
        });
    }

    public boolean removeOfflineBanSync(SteamID steamID) {
        return entityStore.computeInTransaction(txn -> {
            boolean deleted = false;
            final EntityIterable entities = txn.find(OFFLINE_BAN, "player-id", Long.toString(steamID.steamID64()));
            for (Entity entity : entities)
                deleted = entity.delete() || deleted;

            return deleted;
        });
    }

    public List<OfflineBan> getOfflineBansSync() {
        return entityStore.computeInTransaction(txn -> {
            final List<OfflineBan> offlineBans = new ArrayList<>();
            final EntityIterable entities = txn.getAll(OFFLINE_BAN);
            for (Entity entity : entities)
                offlineBans.add(asOfflineBan(entity));
            return offlineBans;
        });
    }

    public Mono<Boolean> addOfflineBan(OfflineBan offlineBan) {
        return Mono.defer(() -> Mono.fromCallable(() -> addOfflineBanSync(offlineBan))
                                    .subscribeOn(Schedulers.elastic()));
    }

    public Mono<Boolean> removeOfflineBan(SteamID steamID) {
        return Mono.defer(() -> Mono.fromCallable(() -> removeOfflineBanSync(steamID))
                                    .subscribeOn(Schedulers.elastic()));
    }

    public Flux<OfflineBan> getOfflineBans() {
        return Flux.defer(() -> Flux.fromIterable(getOfflineBansSync())
                                    .subscribeOn(Schedulers.elastic()));
    }

    public SearchResponse<Ban> searchBansSync(SearchRequest request) throws ParseException, IOException {
        final SearchResponse<String> idsResponse = searchIndex.search(request);
        return entityStore.computeInReadonlyTransaction(txn -> {
            final List<Ban> results = idsResponse.results
                                              .stream()
                                              .map(txn::toEntityId)
                                              .map(txn::getEntity)
                                              .map(this::asBan)
                                              .collect(Collectors.toList());
            return new SearchResponse<>(idsResponse.getFrom(),
                                        idsResponse.getTotal(),
                                        results,
                                        idsResponse.getContinueAfter());
        });
    }

    public Mono<SearchResponse<Ban>> searchBans(SearchRequest request) {
        return Mono.defer(() -> Mono.fromCallable(() -> searchBansSync(request))
                                    .subscribeOn(Schedulers.elastic()));
    }

    public List<BanLogEntry> getBanHistorySync(SteamID steamID) {
        return entityStore.computeInReadonlyTransaction(txn -> {
            final List<BanLogEntry> entries = new ArrayList<>();
            final EntityIterable entities = txn.find(LOG_ENTRY, "player-id", Long.toString(steamID.steamID64()));
            for (Entity entity : entities) {
                final BanLogEntry banLogEntry = asBanLogEntry(entity);
                entries.add(banLogEntry);
            }
            return entries;
        });
    }

    public Flux<BanLogEntry> getBanHistory(SteamID steamID) {
        return Flux.defer(() -> Flux.fromIterable(getBanHistorySync(steamID))
                                    .subscribeOn(Schedulers.elastic()));
    }

    /**
     * Returns ban log entries detected between {@code from} (inclusive) and {@code to} (exclusive).
     */
    public List<BanLogEntry> getBanHistorySync(Instant from, Instant to) {
        return entityStore.computeInReadonlyTransaction(txn -> {
            final List<BanLogEntry> entries = new ArrayList<>();
            // The search here is inclusive on both ends
            final EntityIterable entities = txn.find(LOG_ENTRY, "detected-at", from, to);
            for (Entity entity : entities) {
                final BanLogEntry banLogEntry = asBanLogEntry(entity);
                // Make the search exclusive on the right end
                if (banLogEntry.getDetectedAt().isBefore(to))
                    entries.add(banLogEntry);
            }
            return entries;
        });
    }

    /**
     * Returns ban log entries detected between {@code from} (inclusive) and {@code to} (exclusive).
     */
    public Flux<BanLogEntry> getBanHistory(Instant from, Instant to) {
        return Flux.defer(() -> Flux.fromIterable(getBanHistorySync(from, to))
                                    .subscribeOn(Schedulers.elastic()));
    }

    public @Nullable Instant getLastUpdateSync() {
        return entityStore.computeInReadonlyTransaction(txn -> {
            final var iterable = txn.find("TIMESTAMP", "for", "current-bans");
            return Optional.ofNullable(iterable.getFirst())
                           .map(e -> getProperty(e, "timestamp"))
                           .filter(Instant.class::isInstance)
                           .map(Instant.class::cast)
                           .orElse(null);
        });
    }

    public Mono<Instant> getLastUpdate() {
        return Mono.defer(() -> Mono.fromCallable(this::getLastUpdateSync)
                                    .subscribeOn(Schedulers.elastic()));
    }

    public List<Ban> getCurrentBansSync() {
        return entityStore.computeInReadonlyTransaction(txn -> {
            final List<Ban> currentBans = new ArrayList<>();
            for (Entity entity : txn.getAll(CURRENT_BAN)) {
                final Ban ban = asBan(entity);
                currentBans.add(ban);
            }
            return currentBans;
        });
    }

    private void indexBans() {
        entityStore.executeInTransaction(txn -> {
            // Reset the unique bans
            for (Entity entity : txn.getAll(UNIQUE_BAN))
                entity.delete();

            final EntityIterable entities = txn.getAll(LOG_ENTRY);
            final Set<UniqueBan> uniqueBans =
                    StreamSupport.stream(entities::spliterator, 0, false)
                                 .map(this::asBanLogEntry)
                                 .filter(banLogEntry -> "add".equals(banLogEntry.getAction()))
                                 .map(BanLogEntry::getBan)
                                 .map(UniqueBan::new)
                                 .collect(Collectors.toSet());

            final Map<String, Ban> bans = new HashMap<>();
            for (UniqueBan uniqueBan : uniqueBans) {
                final Entity entity = saveUniqueBan(uniqueBan, txn.newEntity(UNIQUE_BAN));
                bans.put(entity.getId().toString(), uniqueBan.ban);
            }

            try {
                searchIndex.index(bans);
            } catch (IOException e) {
                LOGGER.warn("Failed to index bans", e);
            }
        });
    }

    public Flux<Ban> getCurrentBans() {
        return Flux.defer(() -> Flux.fromIterable(getCurrentBansSync())
                                    .subscribeOn(Schedulers.elastic()));
    }

    public File backup() throws Exception {
        final BackupBean backupBean = new BackupBean(entityStore);
        backupBean.setBackupToZip(true);
        backupBean.setBackupPath(new File(entityStore.getLocation(), "backups").getAbsolutePath());
        backupBean.setBackupNamePrefix("bans_daily_backup-");
        return CompressBackupUtil.backup(backupBean);
    }

    private void setTimestamp(StoreTransaction txn, Instant timestamp) {
        final var iterable = txn.find("TIMESTAMP", "for", "current-bans");
        final var entity = Optional.ofNullable(iterable.getFirst())
                                   .orElseGet(() -> {
                                       final Entity e = txn.newEntity("TIMESTAMP");
                                       e.setProperty("for", "current-bans");
                                       return e;
                                   });

        entity.setProperty("timestamp", timestamp);
    }

    private BanLogEntry asBanLogEntry(Entity entity) {
        final Instant detectedAt = getProperty(entity, "detected-at");
        final String action = getProperty(entity, "action");
        final Ban ban = asBan(entity);

        return new BanLogEntry() {
            @Override
            public Instant getDetectedAt() {
                return detectedAt;
            }

            @Override
            public String getAction() {
                return action;
            }

            @Override
            public Ban getBan() {
                return ban;
            }
        };
    }

    private Ban asBan(Entity entity) {
        return new Ban.Builder()
                .setId(getProperty(entity, "player-id"))
                .setEnactedTime(getProperty(entity, "enacted-time"))
                .setDuration(getProperty(entity, "duration"))
                .setIpPolicy(getProperty(entity, "ip-policy"))
                .setPlayerName(getProperty(entity, "player-name"))
                .setReason(getProperty(entity, "reason"))
                .build();
    }

    private OfflineBan asOfflineBan(Entity entity) {
        return new OfflineBan.Builder()
                .setId(getProperty(entity, "player-id"))
                .setEnactedTime(getProperty(entity, "enacted-time"))
                .setDuration(getProperty(entity, "duration"))
                .setPlayerName(getProperty(entity, "player-name"))
                .setReason(getProperty(entity, "reason"))
                .build();
    }

    private boolean isSameBan(Ban ban, Entity entity) {
        if (entity == null)
            return false;

        final String id = getProperty(entity, "player-id");
        final Instant enactedTime = getProperty(entity, "enacted-time");
        final Duration duration = getProperty(entity, "duration");

        return Objects.equals(id, ban.getId())
                && Objects.equals(enactedTime, ban.getEnactedTime())
                && Objects.equals(duration, ban.getDuration());
    }

    @Contract("_, !null -> param2")
    private Entity saveBan(Ban ban, Entity entity) {
        setProperty(entity, "player-id", ban.getId());
        setProperty(entity, "enacted-time", ban.getEnactedTime());
        setProperty(entity, "duration", ban.getDuration());
        setProperty(entity, "ip-policy", ban.getIpPolicy());
        setProperty(entity, "player-name", ban.getPlayerName());
        setProperty(entity, "reason", ban.getReason());
        return entity;
    }

    private void saveOfflineBan(OfflineBan ban, Entity entity) {
        setProperty(entity, "player-id", ban.getId());
        setProperty(entity, "enacted-time", ban.getEnactedTime());
        setProperty(entity, "duration", ban.getDuration());
        setProperty(entity, "player-name", ban.getPlayerName());
        setProperty(entity, "reason", ban.getReason());
    }

    @Contract("_, !null -> param2")
    private Entity saveUniqueBan(UniqueBan uniqueBan, Entity entity) {
        setProperty(entity, "ban-id", uniqueBan.id);
        return saveBan(uniqueBan.ban, entity);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<?>> T getProperty(Entity entity, String propertyName) {
        if (!entity.getPropertyNames().contains(propertyName))
            return null;
        return (T) entity.getProperty(propertyName);
    }

    private void setProperty(Entity entity, String propertyName, Comparable<?> value) {
        if (value != null)
            entity.setProperty(propertyName, value);
    }

    @Override
    public void close() throws IOException {
        entityStore.close();
        searchIndex.close();
    }

    @SuppressWarnings("unused")
    public interface BanLogEntry {
        Instant getDetectedAt();

        String getAction();

        Ban getBan();
    }

    static class UniqueBan {
        private final @NotNull String id;
        private final @NotNull Ban ban;

        public UniqueBan(@NotNull Ban ban) {
            this.id = MD5(String.format("%s|%s|%s|%s|%s", ban.getId(), ban.getEnactedTime(), ban.getDuration(),
                                        ban.getPlayerName(), ban.getReason()));
            this.ban = ban;
        }

        public @NotNull String getId() {
            return id;
        }

        public @NotNull Ban getBan() {
            return ban;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final UniqueBan uniqueBan = (UniqueBan) o;
            return id.equals(uniqueBan.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", UniqueBan.class.getSimpleName() + "[", "]")
                           .add("id='" + id + "'")
                           .add("ban=" + ban)
                           .toString();
        }
    }

    public interface Stats {
        int numAdded();

        int numRemoved();
    }

    @SuppressWarnings("unused")
    public static class BanInfo {
        private final Ban ban;

        public BanInfo(Ban ban) {
            this.ban = ban;
        }

        public String getId() {
            return ban.getId();
        }

        public String getEnactedTime() {
            return ban.getEnactedTime().toString();
        }

        public String getDuration() {
            return ban.getDuration().toString();
        }

        public String getIpPolicy() {
            return ban.getIpPolicy();
        }

        public String getPlayerName() {
            return ban.getPlayerName();
        }

        public String getReason() {
            return ban.getReason();
        }

        public String getBannedUntil() {
            return ban.getBannedUntil().toString();
        }
    }
}

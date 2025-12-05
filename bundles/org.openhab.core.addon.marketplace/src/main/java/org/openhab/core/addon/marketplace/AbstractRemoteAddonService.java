/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.addon.marketplace;

import static org.openhab.core.common.ThreadPoolManager.THREAD_POOL_NAME_COMMON;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonEventFactory;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonInfoRegistry;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link AbstractRemoteAddonService} implements basic functionality of a remote add-on-service.
 *
 * <p>
 * This abstract class provides a foundation for marketplace-style addon services that:
 * <ul>
 * <li>Fetch addons from remote sources (with 15-minute caching via {@link ExpiringCache})</li>
 * <li>Store installed addon metadata locally for persistence across restarts</li>
 * <li>Delegate install/uninstall operations to registered {@link MarketplaceAddonHandler}s</li>
 * <li>Support remote enable/disable via OSGi configuration</li>
 * <li>Filter incompatible addons based on openHAB version compatibility</li>
 * </ul>
 *
 * <p>
 * <b>Refresh Strategy:</b> The service rebuilds the addon list on every {@link #getAddons} call
 * by combining locally installed addons (from storage) with remote addons (from cache).
 * Remote addons are cached for 15 minutes and only re-fetched when the cache expires.
 *
 * <p>
 * <b>Handler Architecture:</b> Multiple {@link MarketplaceAddonHandler}s can be registered.
 * Each handler declares support for specific addon type + content type combinations.
 * The first matching handler handles install/uninstall operations for that addon.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractRemoteAddonService implements AddonService {
    static final String CONFIG_REMOTE_ENABLED = "remote";
    static final String CONFIG_INCLUDE_INCOMPATIBLE = "includeIncompatible";
    static final Comparator<Addon> BY_COMPATIBLE_AND_VERSION = (addon1, addon2) -> {
        // prefer compatible to incompatible
        int compatible = Boolean.compare(addon2.getCompatible(), addon1.getCompatible());
        if (compatible != 0) {
            return compatible;
        }
        try {
            // Add-on versions often contain a dash instead of a dot as separator for the qualifier (e.g. -SNAPSHOT)
            // This is not a valid format and everything after the dash needs to be removed.
            BundleVersion version1 = new BundleVersion(addon1.getVersion().replaceAll("-.*", ".0"));
            BundleVersion version2 = new BundleVersion(addon2.getVersion().replaceAll("-.*", ".0"));

            // prefer newer version over older
            return version2.compareTo(version1);
        } catch (IllegalArgumentException e) {
            // assume they are equal (for ordering) if we can't compare the versions
            return 0;
        }
    };

    protected final BundleVersion coreVersion;

    protected final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
    protected final Set<MarketplaceAddonHandler> addonHandlers = new HashSet<>();
    protected final Storage<String> installedAddonStorage;
    protected final EventPublisher eventPublisher;
    protected final ConfigurationAdmin configurationAdmin;
    protected final ExpiringCache<List<Addon>> cachedRemoteAddons = new ExpiringCache<>(Duration.ofMinutes(15),
            this::getRemoteAddons);
    protected final AddonInfoRegistry addonInfoRegistry;
    protected List<Addon> cachedAddons = List.of();
    protected List<String> installedAddonIds = List.of();

    private final Logger logger = LoggerFactory.getLogger(AbstractRemoteAddonService.class);
    private final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool(THREAD_POOL_NAME_COMMON);

    protected AbstractRemoteAddonService(EventPublisher eventPublisher, ConfigurationAdmin configurationAdmin,
            StorageService storageService, AddonInfoRegistry addonInfoRegistry, String servicePid) {
        this.addonInfoRegistry = addonInfoRegistry;
        this.eventPublisher = eventPublisher;
        this.configurationAdmin = configurationAdmin;
        this.installedAddonStorage = storageService.getStorage(servicePid);
        this.coreVersion = getCoreVersion();
    }

    protected BundleVersion getCoreVersion() {
        return new BundleVersion(FrameworkUtil.getBundle(OpenHAB.class).getVersion().toString());
    }

    /**
     * Converts a storage entry (JSON string) back into an {@link Addon} object.
     *
     * <p>
     * This method performs two operations:
     * <ol>
     * <li>Deserializes the JSON string into an Addon object</li>
     * <li>Enriches the addon with config description URI from {@link AddonInfoRegistry} if available</li>
     * </ol>
     *
     * <p>
     * <b>Fallback behavior:</b> If no {@link AddonInfo} is found in the registry, or if the
     * config description URI is already present, the stored addon is returned as-is.
     *
     * @param entry the storage entry containing the addon UID as key and JSON string as value
     * @return the deserialized addon, potentially enriched with config description URI
     * @throws JsonSyntaxException if the stored JSON is malformed
     * @throws NullPointerException if the entry value is null
     */
    private Addon convertFromStorage(Map.Entry<String, @Nullable String> entry) {
        Addon storedAddon = Objects.requireNonNull(gson.fromJson(entry.getValue(), Addon.class));
        AddonInfo addonInfo = addonInfoRegistry.getAddonInfo(storedAddon.getType() + "-" + storedAddon.getId());
        if (addonInfo != null && storedAddon.getConfigDescriptionURI().isBlank()) {
            return Addon.create(storedAddon).withConfigDescriptionURI(addonInfo.getConfigDescriptionURI()).build();
        } else {
            return storedAddon;
        }
    }

    @Override
    public void refreshSource() {
        if (!addonHandlers.stream().allMatch(MarketplaceAddonHandler::isReady)) {
            logger.debug("Add-on service '{}' tried to refresh source before add-on handlers ready. Exiting.",
                    getClass());
            return;
        }

        List<Addon> addons = new ArrayList<>();

        // retrieve add-ons that should be available from storage and check if they are really installed
        // this is safe, because the {@link AddonHandler}s only report ready when they installed everything from the
        // cache
        try {
            installedAddonStorage.stream().map(this::convertFromStorage).forEach(addon -> {
                setInstalled(addon);
                addons.add(addon);
            });
        } catch (JsonSyntaxException e) {
            // Corrupted storage detected - purge all entries to prevent repeated failures
            int entryCount = installedAddonStorage.getKeys().size();
            List<String> affectedAddons = List.copyOf(installedAddonStorage.getKeys());
            logger.error(
                    "Storage contains malformed JSON for service '{}'. Purging {} corrupted entries. Affected addons: {}",
                    getId(), entryCount, affectedAddons, e);
            affectedAddons.forEach(installedAddonStorage::remove);
            logger.warn(
                    "Storage purged successfully. The {} affected addon(s) must be reinstalled manually from the '{}' marketplace.",
                    entryCount, getId());
            // Note: NOT recursing to avoid potential infinite loop if storage continues to fail
            return; // Exit refresh early after purge - let next scheduled refresh retry
        } catch (NullPointerException e) {
            logger.error(
                    "Null value encountered in storage for service '{}'. This indicates storage corruption. Consider restarting openHAB or clearing storage manually.",
                    getId(), e);
            return;
        }

        // remove not installed add-ons from the add-ons list, but remember their UIDs to re-install them
        List<String> missingAddons = addons.stream().filter(addon -> !addon.isInstalled()).map(Addon::getUid).toList();
        missingAddons.forEach(installedAddonStorage::remove);
        addons.removeIf(addon -> missingAddons.contains(addon.getUid()));

        // create lookup list to make sure installed addons take precedence
        List<String> currentAddonIds = addons.stream().map(Addon::getUid).toList();

        // get the remote addons
        if (remoteEnabled()) {
            List<Addon> remoteAddons = Objects.requireNonNullElse(cachedRemoteAddons.getValue(), List.of());
            remoteAddons.stream().filter(a -> !currentAddonIds.contains(a.getUid())).forEach(addon -> {
                setInstalled(addon);
                addons.add(addon);
            });
        }

        // remove incompatible add-ons if not enabled
        boolean showIncompatible = includeIncompatible();
        addons.removeIf(addon -> !addon.isInstalled() && !addon.getCompatible() && !showIncompatible);

        // check and remove duplicate uids
        Map<String, List<Addon>> addonMap = new HashMap<>();
        addons.forEach(a -> addonMap.computeIfAbsent(a.getUid(), k -> new ArrayList<>()).add(a));
        for (List<Addon> partialAddonList : addonMap.values()) {
            if (partialAddonList.size() > 1) {
                partialAddonList.stream().sorted(BY_COMPATIBLE_AND_VERSION).skip(1).forEach(addons::remove);
            }
        }

        cachedAddons = addons;
        this.installedAddonIds = currentAddonIds;

        if (!missingAddons.isEmpty()) {
            logger.info("Re-installing missing add-ons from remote repository: {}", missingAddons);
            scheduler.execute(() -> missingAddons.forEach(this::install));
        }
    }

    /**
     * Updates the installed state of an addon by querying all registered handlers.
     *
     * <p>
     * An addon is considered installed if <b>any</b> handler reports it as installed.
     * This check is performed by iterating through all {@link MarketplaceAddonHandler}s
     * and calling {@link MarketplaceAddonHandler#isInstalled(String)}.
     *
     * <p>
     * <b>Note:</b> This method mutates the addon object's installed state.
     *
     * @param addon the addon whose installed state should be updated
     */
    private void setInstalled(Addon addon) {
        addon.setInstalled(addonHandlers.stream().anyMatch(h -> h.isInstalled(addon.getUid())));
    }

    /**
     * Add a {@link MarketplaceAddonHandler} to this service
     *
     * This needs to be implemented by the addon-services because the handlers are references to OSGi services and
     * the @Reference annotation is not inherited.
     * It is added here to make sure that implementations comply with that.
     *
     * @param handler the handler that shall be added
     */
    protected abstract void addAddonHandler(MarketplaceAddonHandler handler);

    /**
     * Remove a {@link MarketplaceAddonHandler} from this service
     *
     * This needs to be implemented by the addon-services because the handlers are references to OSGi services and
     * unbind methods can't be inherited.
     * It is added here to make sure that implementations comply with that.
     *
     * @param handler the handler that shall be removed
     */
    protected abstract void removeAddonHandler(MarketplaceAddonHandler handler);

    /**
     * get all addons from remote
     *
     * @return a list of {@link Addon} that are available on the remote side
     */
    protected abstract List<Addon> getRemoteAddons();

    @Override
    public List<Addon> getAddons(@Nullable Locale locale) {
        refreshSource();
        return cachedAddons;
    }

    @Override
    public List<AddonType> getTypes(@Nullable Locale locale) {
        return AddonType.DEFAULT_TYPES;
    }

    @Override
    public void install(String id) {
        Addon addon = getAddon(id, null);
        if (addon == null) {
            postFailureEvent(id, "Add-on can't be installed because it is not known.");
            return;
        }
        for (MarketplaceAddonHandler handler : addonHandlers) {
            if (handler.supports(addon.getType(), addon.getContentType())) {
                if (!handler.isInstalled(addon.getUid())) {
                    try {
                        handler.install(addon);
                        addon.setInstalled(true);
                        installedAddonStorage.put(id, gson.toJson(addon));
                        cachedRemoteAddons.invalidateValue();
                        refreshSource();
                        postInstalledEvent(addon.getUid());
                    } catch (MarketplaceHandlerException e) {
                        postFailureEvent(addon.getUid(), e.getMessage());
                    }
                } else {
                    postFailureEvent(addon.getUid(), "Add-on is already installed.");
                }
                return;
            }
        }
        postFailureEvent(id, "Add-on can't be installed because there is no handler for it.");
    }

    @Override
    public void uninstall(String id) {
        Addon addon = getAddon(id, null);
        if (addon == null) {
            postFailureEvent(id, "Add-on can't be uninstalled because it is not known.");
            return;
        }
        for (MarketplaceAddonHandler handler : addonHandlers) {
            if (handler.supports(addon.getType(), addon.getContentType())) {
                if (handler.isInstalled(addon.getUid())) {
                    try {
                        handler.uninstall(addon);
                        installedAddonStorage.remove(id);
                        cachedRemoteAddons.invalidateValue();
                        refreshSource();
                        postUninstalledEvent(addon.getUid());
                    } catch (MarketplaceHandlerException e) {
                        postFailureEvent(addon.getUid(), e.getMessage());
                    }
                } else {
                    installedAddonStorage.remove(id);
                    postFailureEvent(addon.getUid(), "Add-on is not installed.");
                }
                return;
            }
        }
        postFailureEvent(id, "Add-on can't be uninstalled because there is no handler for it.");
    }

    /**
     * Checks if remote addon fetching is enabled via OSGi configuration.
     *
     * <p>
     * Reads the {@code remote} property from the {@code org.openhab.addons} configuration PID.
     * This allows users to disable network access to remote addon sources.
     *
     * <p>
     * <b>Default behavior:</b> If the configuration is unavailable or the property is not set,
     * remote access is <b>enabled by default</b> (returns {@code true}).
     *
     * @return {@code true} if remote addon sources should be queried, {@code false} otherwise
     */
    protected boolean remoteEnabled() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration("org.openhab.addons", null);
            Dictionary<String, Object> properties = configuration.getProperties();
            if (properties == null) {
                // if we can't determine a set property, we use true (default is remote enabled)
                return true;
            }
            return ConfigParser.valueAsOrElse(properties.get(CONFIG_REMOTE_ENABLED), Boolean.class, true);
        } catch (IOException e) {
            logger.debug("Failed to read remote configuration, defaulting to enabled", e);
            return true;
        }
    }

    /**
     * Checks if incompatible addons should be shown in the addon list.
     *
     * <p>
     * Reads the {@code includeIncompatible} property from the {@code org.openhab.addons} configuration PID.
     * Incompatible addons are those whose version range does not include the current openHAB core version.
     *
     * <p>
     * <b>Default behavior:</b> If the configuration is unavailable or the property is not set,
     * only compatible addons are shown (returns {@code false}).
     *
     * <p>
     * <b>Example:</b> An addon marked compatible with {@code [3.0.0,4.0.0)} will be hidden
     * when running openHAB 4.1.0 unless this setting is enabled.
     *
     * @return {@code true} if incompatible addons should be included in results, {@code false} otherwise
     */
    protected boolean includeIncompatible() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration("org.openhab.addons", null);
            Dictionary<String, Object> properties = configuration.getProperties();
            if (properties == null) {
                // if we can't determine a set property, we use false (default is show compatible only)
                return false;
            }
            return ConfigParser.valueAsOrElse(properties.get(CONFIG_INCLUDE_INCOMPATIBLE), Boolean.class, false);
        } catch (IOException e) {
            logger.debug("Failed to read includeIncompatible configuration, defaulting to false", e);
            return false;
        }
    }

    private void postInstalledEvent(String extensionId) {
        Event event = AddonEventFactory.createAddonInstalledEvent(extensionId);
        eventPublisher.post(event);
    }

    private void postUninstalledEvent(String extensionId) {
        Event event = AddonEventFactory.createAddonUninstalledEvent(extensionId);
        eventPublisher.post(event);
    }

    private void postFailureEvent(String extensionId, @Nullable String msg) {
        Event event = AddonEventFactory.createAddonFailureEvent(extensionId, msg);
        eventPublisher.post(event);
    }
}

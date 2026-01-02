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
package org.openhab.core.model.thing.internal;

import static org.openhab.core.model.core.ModelCoreConstants.isIsolatedModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.common.registry.AbstractProvider;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.model.item.BindingConfigParseException;
import org.openhab.core.model.item.BindingConfigReader;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.link.ItemChannelLink;
import org.openhab.core.thing.link.ItemChannelLinkProvider;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GenericItemChannelLinkProvider} link items to channel by reading bindings with type "channel".
 *
 * @author Oliver Libutzki - Initial contribution
 * @author Alex Tugarev - Added parsing of multiple Channel UIDs
 * @author Laurent Garnier - Store channel links per context (model) + do not notify the registry for isolated models
 */
@NonNullByDefault
@Component(immediate = true, service = { GenericItemChannelLinkProvider.class, ItemChannelLinkProvider.class,
        BindingConfigReader.class })
public class GenericItemChannelLinkProvider extends AbstractProvider<ItemChannelLink>
        implements BindingConfigReader, ItemChannelLinkProvider {

    private final Logger logger = LoggerFactory.getLogger(GenericItemChannelLinkProvider.class);

    /**
     * Caches binding configurations.
     * Structure: context -> itemName -> channelUID -> ItemChannelLink
     * This multi-level map enables fast lookup and isolation of links per context.
     */
    protected Map<String, Map<String, Map<ChannelUID, ItemChannelLink>>> itemChannelLinkMap = new ConcurrentHashMap<>();

    /**
     * Tracks channels added during the current configuration update per context.
     * Structure: context -> itemName -> Set<ChannelUID>
     * Used to detect which channels were removed during an update transaction.
     * Per-context to support concurrent updates across different models.
     * FIXED: Was previously global (addedItemChannels), causing concurrent update issues.
     */
    private final Map<String, Map<String, Set<ChannelUID>>> addedItemChannelsByContext = new ConcurrentHashMap<>();

    /**
     * Stores information about the context of items.
     * Structure: context -> Set of Item names
     * Tracks which items are managed in each context for lifecycle management.
     */
    protected Map<String, Set<String>> contextMap = new ConcurrentHashMap<>();

    /**
     * Tracks item names present at the start of a configuration update transaction per context.
     * Structure: context -> Set of Item names
     * Per-context to support concurrent updates across different models.
     * This prevents race conditions when multiple configuration updates occur simultaneously.
     * FIXED: Was previously global (previousItemNames), causing concurrent update issues.
     */
    private final Map<String, Set<String>> previousItemNamesByContext = new ConcurrentHashMap<>();

    @Override
    public String getBindingType() {
        return "channel";
    }

    @Override
    public void validateItemType(String itemType, String bindingConfig) throws BindingConfigParseException {
        // all item types are allowed
    }

    /**
     * Processes a binding configuration string containing one or more Channel UIDs.
     *
     * Expected format: one or more comma-separated Channel UIDs.
     * Example: "binding:thing:device:channel1,binding:thing:device:channel2"
     *
     * @param context the model context (e.g., file name or model name) for grouping related links
     * @param itemType the type of the item (unused, all types are allowed)
     * @param itemName the name of the item to link
     * @param bindingConfig comma-separated Channel UIDs; at least one UID is required
     * @param configuration configuration parameters for the link (e.g., profile settings)
     * @throws BindingConfigParseException if no valid Channel UIDs are provided or if a UID is malformed
     */
    @Override
    public void processBindingConfiguration(String context, String itemType, String itemName, String bindingConfig,
            Configuration configuration) throws BindingConfigParseException {
        // Split by comma, trim each entry, and filter out blank tokens
        String[] rawUids = bindingConfig.split(",");
        java.util.List<String> uids = java.util.Arrays.stream(rawUids).map(String::trim).filter(uid -> !uid.isEmpty())
                .toList();

        if (uids.isEmpty()) {
            throw new BindingConfigParseException(
                    "At least one Channel UID should be provided: <bindingID>.<thingTypeId>.<thingId>.<channelId>");
        }
        for (String uid : uids) {
            createItemChannelLink(context, itemName, uid, configuration);
        }
    }

    private void createItemChannelLink(String context, String itemName, String channelUID, Configuration configuration)
            throws BindingConfigParseException {
        ChannelUID channelUIDObject;
        try {
            channelUIDObject = new ChannelUID(channelUID);
        } catch (IllegalArgumentException e) {
            throw new BindingConfigParseException(e.getMessage());
        }

        // Fix the configuration in case a profile is defined without any scope.
        // Profiles must include a scope (e.g., "system:default" not just "default").
        if (configuration.containsKey("profile") && configuration.get("profile") instanceof String profile
                && !profile.contains(":")) {
            String fullProfile = ProfileTypeUID.SYSTEM_SCOPE + ":" + profile;
            configuration.put("profile", fullProfile);
            logger.info(
                    "Profile '{}' for channel '{}' is missing the scope prefix, assuming the correct UID is '{}'. Check your configuration.",
                    profile, channelUID, fullProfile);
        }

        ItemChannelLink itemChannelLink = new ItemChannelLink(itemName, channelUIDObject, configuration);

        Set<String> itemNames = Objects.requireNonNull(contextMap.computeIfAbsent(context, k -> new HashSet<>()));
        itemNames.add(itemName);

        // Remove from previousItemNames for this context if present (item is being reconfigured)
        Set<String> previousItems = previousItemNamesByContext.get(context);
        if (previousItems != null) {
            previousItems.remove(itemName);
        }

        Map<String, Map<ChannelUID, ItemChannelLink>> channelLinkMap = Objects
                .requireNonNull(itemChannelLinkMap.computeIfAbsent(context, k -> new ConcurrentHashMap<>()));
        // Create a HashMap with an initial capacity of 2 (the default is 16) to save memory because most items have
        // only one channel. A capacity of 2 is enough to avoid resizing the HashMap in most cases, whereas 1 would
        // trigger a resize as soon as one element is added.
        Map<ChannelUID, ItemChannelLink> links = Objects
                .requireNonNull(channelLinkMap.computeIfAbsent(itemName, k -> new HashMap<>(2)));

        ItemChannelLink oldLink = links.put(channelUIDObject, itemChannelLink);
        if (isValidContextForListeners(context)) {
            if (oldLink == null) {
                notifyListenersAboutAddedElement(itemChannelLink);
            } else {
                notifyListenersAboutUpdatedElement(oldLink, itemChannelLink);
            }
        }

        // Track this channel as added during the current update transaction for this context
        addedItemChannelsByContext.computeIfAbsent(context, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemName, k -> new HashSet<>(2)).add(channelUIDObject);
    }

    /**
     * Marks the start of a configuration update transaction for the given context.
     * This captures the current set of items in the context to detect removed items during the update.
     *
     * When called, this method records which items currently exist in the context.
     * During {@link #processBindingConfiguration}, items that are processed are removed from this set.
     * After update completion (in {@link #stopConfigurationUpdate}), any items remaining in the set
     * are considered removed and their links are cleaned up.
     *
     * @param context the model context for which to start the update transaction
     */
    @Override
    public void startConfigurationUpdate(String context) {
        if (previousItemNamesByContext.containsKey(context)) {
            logger.warn("There already is an update transaction for context '{}'. Continuing anyway.", context);
        }
        Set<String> previous = contextMap.get(context);
        previousItemNamesByContext.put(context, previous != null ? new HashSet<>(previous) : new HashSet<>());
    }

    /**
     * Marks the end of a configuration update transaction for the given context.
     * This removes any items and channels that were not reprocessed during the update.
     *
     * Cleanup logic:
     * 1. Remove items that existed before but were not processed (deleted items).
     * 2. Remove channels within processed items that were not reprocessed (channels removed from item).
     * 3. Notify listeners about removed links (if context is not isolated).
     * 4. Clean up transaction state for this context.
     *
     * @param context the model context for which to stop the update transaction
     */
    @Override
    public void stopConfigurationUpdate(String context) {
        final Set<String> previousItemNames = previousItemNamesByContext.remove(context);
        if (previousItemNames == null) {
            logger.debug("stopConfigurationUpdate called for context '{}' but no active transaction found.", context);
            return;
        }

        Map<String, Map<ChannelUID, ItemChannelLink>> channelLinkMap = itemChannelLinkMap.getOrDefault(context,
                new HashMap<>());

        // Remove items that existed before but were not reprocessed (deleted items)
        for (String itemName : previousItemNames) {
            Map<ChannelUID, ItemChannelLink> links = channelLinkMap.remove(itemName);
            if (links != null && isValidContextForListeners(context)) {
                links.values().forEach(this::notifyListenersAboutRemovedElement);
            }
        }
        Optional.ofNullable(contextMap.get(context)).ifPresent(ctx -> ctx.removeAll(previousItemNames));

        // For items that were reprocessed, remove channels that are no longer present
        Map<String, Set<ChannelUID>> addedItemChannels = addedItemChannelsByContext.remove(context);
        if (addedItemChannels != null) {
            addedItemChannels.forEach((itemName, addedChannelUIDs) -> {
                Map<ChannelUID, ItemChannelLink> links = channelLinkMap.getOrDefault(itemName, Map.of());
                Set<ChannelUID> removedChannelUIDs = new HashSet<>(links.keySet());
                removedChannelUIDs.removeAll(addedChannelUIDs);
                removedChannelUIDs.forEach(removedChannelUID -> {
                    ItemChannelLink link = links.remove(removedChannelUID);
                    if (link != null && isValidContextForListeners(context)) {
                        notifyListenersAboutRemovedElement(link);
                    }
                });
            });
        }

        // Clean up empty context entry
        if (channelLinkMap.isEmpty()) {
            itemChannelLinkMap.remove(context);
        }
    }

    /**
     * Returns all item-to-channel links from non-isolated (valid listener) contexts.
     *
     * Isolated models (used for parsing only) are excluded from this result.
     * This ensures that UI and runtime consumers only see links from active models.
     *
     * @return collection of all valid ItemChannelLink objects
     */
    @Override
    public Collection<ItemChannelLink> getAll() {
        return itemChannelLinkMap.entrySet().stream().filter(entry -> isValidContextForListeners(entry.getKey()))
                .flatMap(entry -> entry.getValue().values().stream()).flatMap(itemLinks -> itemLinks.values().stream())
                .toList();
    }

    /**
     * Returns all item-to-channel links for a specific context (model).
     *
     * This includes links from both isolated and non-isolated contexts.
     * Useful for model-specific operations like validation or export.
     *
     * @param context the model context (e.g., file name or model name)
     * @return collection of ItemChannelLink objects in this context
     */
    public Collection<ItemChannelLink> getAllFromContext(String context) {
        return itemChannelLinkMap.getOrDefault(context, Map.of()).values().stream()
                .flatMap(itemLinks -> itemLinks.values().stream()).toList();
    }

    private boolean isValidContextForListeners(String context) {
        // Ignore isolated models
        return !isIsolatedModel(context);
    }
}

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
package org.openhab.core.automation.module.script.providersupport.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.module.script.ScriptExtensionProvider;
import org.openhab.core.automation.module.script.providersupport.shared.ProviderItemChannelLinkRegistry;
import org.openhab.core.automation.module.script.providersupport.shared.ProviderItemRegistryDelegate;
import org.openhab.core.automation.module.script.providersupport.shared.ProviderMetadataRegistryDelegate;
import org.openhab.core.automation.module.script.providersupport.shared.ProviderThingRegistryDelegate;
import org.openhab.core.automation.module.script.providersupport.shared.ScriptedItemChannelLinkProvider;
import org.openhab.core.automation.module.script.providersupport.shared.ScriptedItemProvider;
import org.openhab.core.automation.module.script.providersupport.shared.ScriptedMetadataProvider;
import org.openhab.core.automation.module.script.providersupport.shared.ScriptedThingProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link ProviderScriptExtension} extends scripts to provide openHAB entities like items, things, metadata, and
 * item-channel links.
 * <p>
 * This extension manages the lifecycle of script-provided entities, automatically removing them when the script is
 * unloaded or reloaded. This prevents orphaned entities from remaining in the system after script termination.
 * <p>
 * <b>Architecture:</b>
 * <ul>
 * <li>Each script instance is identified by a unique {@code scriptIdentifier}</li>
 * <li>Registry delegates are cached per script to maintain entity ownership tracking</li>
 * <li>On script unload, all entities added by that script are automatically removed</li>
 * <li>Thread-safe: Uses {@link ConcurrentHashMap} for registry caching</li>
 * </ul>
 * <p>
 * <b>Usage Example:</b>
 *
 * <pre>
 * // In a script (JavaScript/Groovy):
 * var itemRegistry = scriptExtension.get("myScript", "itemRegistry");
 * var item = new StringItem("MyScriptItem");
 * itemRegistry.add(item); // Item will be auto-removed when script unloads
 * </pre>
 *
 * @author Florian Hotze - Initial contribution
 */
@Component(immediate = true)
@NonNullByDefault
public class ProviderScriptExtension implements ScriptExtensionProvider {
    private static final String PRESET_NAME = "provider";
    private static final String ITEM_CHANNEL_LINK_REGISTRY_NAME = "itemChannelLinkRegistry";
    private static final String ITEM_REGISTRY_NAME = "itemRegistry";
    private static final String METADATA_REGISTRY_NAME = "metadataRegistry";
    private static final String THING_REGISTRY_NAME = "thingRegistry";

    /**
     * Cache of registry delegates per script identifier.
     * <p>
     * Key: scriptIdentifier (unique per script instance)<br>
     * Value: Map of registry type → ProviderRegistry instance
     * <p>
     * This cache ensures that each script gets the same registry instance across multiple {@code get()} calls,
     * enabling proper tracking of which entities were added by which script.
     * <p>
     * Thread-safe: ConcurrentHashMap allows safe concurrent access from multiple scripts.
     */
    private final Map<String, Map<String, ProviderRegistry>> registryCache = new ConcurrentHashMap<>();

    private final ItemChannelLinkRegistry itemChannelLinkRegistry;
    private final ScriptedItemChannelLinkProvider itemChannelLinkProvider;
    private final ItemRegistry itemRegistry;
    private final ScriptedItemProvider itemProvider;
    private final MetadataRegistry metadataRegistry;
    private final ScriptedMetadataProvider metadataProvider;
    private final ThingRegistry thingRegistry;
    private final ScriptedThingProvider thingProvider;

    @Activate
    public ProviderScriptExtension( //
            final @Reference ItemChannelLinkRegistry itemChannelLinkRegistry,
            final @Reference ScriptedItemChannelLinkProvider itemChannelLinkProvider,
            final @Reference ItemRegistry itemRegistry, final @Reference ScriptedItemProvider itemProvider,
            final @Reference MetadataRegistry metadataRegistry,
            final @Reference ScriptedMetadataProvider metadataProvider, final @Reference ThingRegistry thingRegistry,
            final @Reference ScriptedThingProvider thingProvider) {
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
        this.itemChannelLinkProvider = itemChannelLinkProvider;
        this.itemRegistry = itemRegistry;
        this.itemProvider = itemProvider;
        this.metadataRegistry = metadataRegistry;
        this.metadataProvider = metadataProvider;
        this.thingRegistry = thingRegistry;
        this.thingProvider = thingProvider;
    }

    @Override
    public Collection<String> getDefaultPresets() {
        return Set.of();
    }

    @Override
    public Collection<String> getPresets() {
        return Set.of(PRESET_NAME);
    }

    @Override
    public Collection<String> getTypes() {
        return Set.of(ITEM_CHANNEL_LINK_REGISTRY_NAME, ITEM_REGISTRY_NAME, METADATA_REGISTRY_NAME, THING_REGISTRY_NAME);
    }

    /**
     * Retrieves or creates a registry delegate for the specified script and type.
     * <p>
     * This method implements lazy initialization with caching:
     * <ol>
     * <li>If a registry of the requested type already exists for this script, return it (cached)</li>
     * <li>Otherwise, create a new registry instance and cache it for future calls</li>
     * </ol>
     * <p>
     * <b>Registry Lifecycle:</b>
     * <ul>
     * <li>Created on first access per script</li>
     * <li>Cached until script unload</li>
     * <li>Automatically cleaned up in {@link #unload(String)}</li>
     * </ul>
     * <p>
     * <b>Thread Safety:</b> This method is thread-safe due to ConcurrentHashMap usage. Multiple scripts can
     * concurrently request registries without synchronization issues.
     *
     * @param scriptIdentifier unique identifier for the script instance (must not be null or blank)
     * @param type registry type (must be one of: "itemChannelLinkRegistry", "itemRegistry", "metadataRegistry",
     *            "thingRegistry")
     * @return the registry delegate instance, or {@code null} if the type is not recognized
     * @throws IllegalArgumentException if scriptIdentifier is null or blank, or if type is null
     */
    @Override
    public @Nullable Object get(String scriptIdentifier, String type) throws IllegalArgumentException {
        // Validate inputs
        if (scriptIdentifier.isBlank()) {
            throw new IllegalArgumentException("scriptIdentifier must not be blank");
        }

        // Get or create registry map for this script (thread-safe via ConcurrentHashMap)
        Map<String, ProviderRegistry> registries = Objects
                .requireNonNull(registryCache.computeIfAbsent(scriptIdentifier, k -> new HashMap<>()));

        // Check if registry already exists (cached)
        ProviderRegistry registry = registries.get(type);
        if (registry != null) {
            return registry;
        }

        // Create new registry based on type
        return switch (type) {
            case ITEM_CHANNEL_LINK_REGISTRY_NAME -> {
                ProviderItemChannelLinkRegistry providerItemChannelLinkRegistry = new ProviderItemChannelLinkRegistry(
                        itemChannelLinkRegistry, itemChannelLinkProvider);
                registries.put(type, providerItemChannelLinkRegistry);
                yield providerItemChannelLinkRegistry;
            }
            case ITEM_REGISTRY_NAME -> {
                ProviderItemRegistryDelegate itemRegistryDelegate = new ProviderItemRegistryDelegate(itemRegistry,
                        itemProvider);
                registries.put(ITEM_REGISTRY_NAME, itemRegistryDelegate);
                yield itemRegistryDelegate;
            }
            case METADATA_REGISTRY_NAME -> {
                ProviderMetadataRegistryDelegate metadataRegistryDelegate = new ProviderMetadataRegistryDelegate(
                        metadataRegistry, metadataProvider);
                registries.put(METADATA_REGISTRY_NAME, metadataRegistryDelegate);
                yield metadataRegistryDelegate;
            }
            case THING_REGISTRY_NAME -> {
                ProviderThingRegistryDelegate thingRegistryDelegate = new ProviderThingRegistryDelegate(thingRegistry,
                        thingProvider);
                registries.put(THING_REGISTRY_NAME, thingRegistryDelegate);
                yield thingRegistryDelegate;
            }
            default -> null; // Unknown type - return null instead of throwing exception for compatibility
        };
    }

    @Override
    public Map<String, Object> importPreset(String scriptIdentifier, String preset) {
        if (PRESET_NAME.equals(preset)) {
            return Map.of(ITEM_CHANNEL_LINK_REGISTRY_NAME,
                    Objects.requireNonNull(get(scriptIdentifier, ITEM_CHANNEL_LINK_REGISTRY_NAME)), ITEM_REGISTRY_NAME,
                    Objects.requireNonNull(get(scriptIdentifier, ITEM_REGISTRY_NAME)), METADATA_REGISTRY_NAME,
                    Objects.requireNonNull(get(scriptIdentifier, METADATA_REGISTRY_NAME)), THING_REGISTRY_NAME,
                    Objects.requireNonNull(get(scriptIdentifier, THING_REGISTRY_NAME)));
        }

        return Map.of();
    }

    /**
     * Unloads a script and removes all entities it provided.
     * <p>
     * This method is called when a script is unloaded or reloaded. It performs the following cleanup:
     * <ol>
     * <li>Removes the script's registry cache entry</li>
     * <li>Calls {@link ProviderRegistry#removeAllAddedByScript()} on each registry</li>
     * <li>Ensures all items, things, metadata, and links added by the script are removed</li>
     * </ol>
     * <p>
     * <b>Thread Safety:</b> Safe to call concurrently for different scripts due to ConcurrentHashMap usage.
     * However, concurrent unload calls for the same script are handled safely (idempotent operation).
     *
     * @param scriptIdentifier unique identifier for the script to unload
     */
    @Override
    public void unload(String scriptIdentifier) {
        Map<String, ProviderRegistry> registries = registryCache.remove(scriptIdentifier);
        if (registries != null) {
            // Remove all entities provided by this script from all registries
            for (ProviderRegistry registry : registries.values()) {
                registry.removeAllAddedByScript();
            }
        }
    }
}

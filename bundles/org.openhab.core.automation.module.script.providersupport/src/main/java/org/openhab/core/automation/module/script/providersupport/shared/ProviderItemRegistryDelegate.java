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
package org.openhab.core.automation.module.script.providersupport.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.module.script.providersupport.internal.ProviderRegistry;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemRegistry;

/**
 * The {@link ProviderItemRegistryDelegate} is wrapping a {@link ItemRegistry} to provide a comfortable way to provide
 * items from scripts without worrying about the need to remove items again when the script is unloaded.
 * Nonetheless, using the {@link #addPermanent(Item)} method it is still possible to add items permanently.
 * <p>
 * Use a new instance of this class for each {@link javax.script.ScriptEngine}.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class ProviderItemRegistryDelegate implements ItemRegistry, ProviderRegistry {
    private final ItemRegistry itemRegistry;

    private final Set<String> items = new HashSet<>();

    private final ScriptedItemProvider scriptedProvider;

    public ProviderItemRegistryDelegate(ItemRegistry itemRegistry, ScriptedItemProvider scriptedProvider) {
        this.itemRegistry = itemRegistry;
        this.scriptedProvider = scriptedProvider;
    }

    @Override
    public void addRegistryChangeListener(RegistryChangeListener<Item> listener) {
        itemRegistry.addRegistryChangeListener(listener);
    }

    @Override
    public Collection<Item> getAll() {
        return itemRegistry.getAll();
    }

    @Override
    public Stream<Item> stream() {
        return itemRegistry.stream();
    }

    @Override
    public @Nullable Item get(String key) {
        return itemRegistry.get(key);
    }

    @Override
    public void removeRegistryChangeListener(RegistryChangeListener<Item> listener) {
        itemRegistry.removeRegistryChangeListener(listener);
    }

    @Override
    public Item add(Item element) {
        String itemName = element.getName();
        // Check for item already existing here because the item might exist in a different provider, so we need to
        // check the registry and not only the provider itself
        if (get(itemName) != null) {
            throw new IllegalArgumentException(
                    "Cannot add item, because an item with same name (" + itemName + ") already exists.");
        }

        scriptedProvider.add(element);
        items.add(itemName);

        return element;
    }

    /**
     * Add an item permanently to the registry.
     * This item will be kept in the registry even if the script is unloaded.
     * 
     * @param element the item to be added (must not be null)
     * @return the added item
     */
    public Item addPermanent(Item element) {
        return itemRegistry.add(element);
    }

    @Override
    public @Nullable Item update(Item element) {
        if (items.contains(element.getName())) {
            return scriptedProvider.update(element);
        }
        return itemRegistry.update(element);
    }

    @Override
    public @Nullable Item remove(String key) {
        if (items.remove(key)) {
            return scriptedProvider.remove(key);
        }

        return itemRegistry.remove(key);
    }

    @Override
    public Item getItem(String name) throws ItemNotFoundException {
        return itemRegistry.getItem(name);
    }

    @Override
    public Item getItemByPattern(String name) throws ItemNotFoundException, ItemNotUniqueException {
        return itemRegistry.getItemByPattern(name);
    }

    @Override
    public Collection<Item> getItems() {
        return itemRegistry.getItems();
    }

    @Override
    public Collection<Item> getItemsOfType(String type) {
        return itemRegistry.getItemsOfType(type);
    }

    @Override
    public Collection<Item> getItems(String pattern) {
        return itemRegistry.getItems(pattern);
    }

    @Override
    public Collection<Item> getItemsByTag(String... tags) {
        return itemRegistry.getItemsByTag(tags);
    }

    @Override
    public Collection<Item> getItemsByTagAndType(String type, String... tags) {
        return itemRegistry.getItemsByTagAndType(type, tags);
    }

    @Override
    public <T extends Item> Collection<T> getItemsByTag(Class<T> typeFilter, String... tags) {
        return itemRegistry.getItemsByTag(typeFilter, tags);
    }

    @Override
    public @Nullable Item remove(String itemName, boolean recursive) {
        Item item = get(itemName);
        if (recursive && item instanceof GroupItem groupItem) {
            for (String member : getMemberNamesRecursively(groupItem, getAll())) {
                remove(member);
            }
        }
        if (item != null) {
            remove(item.getName());
            return item;
        } else {
            return null;
        }
    }

    @Override
    public void removeAllAddedByScript() {
        for (String item : items) {
            scriptedProvider.remove(item);
        }
        items.clear();
    }

    /**
     * Recursively collects names of all items that are members of the specified group, including nested groups.
     * <p>
     * This method performs a depth-first traversal of the group hierarchy:
     * <ol>
     * <li>Scans all items to find direct members of the specified group</li>
     * <li>For each member that is itself a GroupItem, recursively collects its members</li>
     * <li>Returns the flattened list of all member names (direct and transitive)</li>
     * </ol>
     * <p>
     * <b>Example:</b>
     *
     * <pre>
     * Group:Number:AVG gTemperature
     * Group gLivingRoom (gTemperature)
     * Number Temperature_LivingRoom (gLivingRoom)
     * Number Temperature_Kitchen (gTemperature)
     *
     * getMemberNamesRecursively(gTemperature) returns:
     * ["gLivingRoom", "Temperature_LivingRoom", "Temperature_Kitchen"]
     * </pre>
     * <p>
     * <b>Performance Note:</b> This method performs an O(n) scan of all items for each group level. For deeply
     * nested groups, consider caching the group hierarchy if performance becomes an issue.
     *
     * @param groupItem the group whose members should be collected
     * @param allItems all items in the registry (used for scanning)
     * @return list of all member item names (direct and nested)
     */
    private List<String> getMemberNamesRecursively(GroupItem groupItem, Collection<Item> allItems) {
        List<String> memberNames = new ArrayList<>();
        for (Item item : allItems) {
            // Check if this item is a member of the specified group
            if (item.getGroupNames().contains(groupItem.getName())) {
                memberNames.add(item.getName());
                // If this member is itself a group, recursively collect its members
                if (item instanceof GroupItem groupItem1) {
                    memberNames.addAll(getMemberNamesRecursively(groupItem1, allItems));
                }
            }
        }
        return memberNames;
    }
}

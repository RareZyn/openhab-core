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
package org.openhab.core.addon;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * The {@link AddonInfoRegistry} provides access to {@link AddonInfo} objects.
 * It tracks {@link AddonInfoProvider} <i>OSGi</i> services to collect all {@link AddonInfo} objects.
 *
 * @author Dennis Nobel - Initial contribution
 * @author Michael Grammling - Initial contribution, added locale support
 */
@Component(immediate = true, service = AddonInfoRegistry.class)
@NonNullByDefault
public class AddonInfoRegistry {

    private final Collection<AddonInfoProvider> addonInfoProviders = new CopyOnWriteArrayList<>();

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addAddonInfoProvider(AddonInfoProvider addonInfoProvider) {
        addonInfoProviders.add(addonInfoProvider);
    }

    public void removeAddonInfoProvider(AddonInfoProvider addonInfoProvider) {
        addonInfoProviders.remove(addonInfoProvider);
    }

    /**
     * Returns the add-on information for the specified add-on UID, or {@code null} if no add-on information could be
     * found.
     *
     * @param uid the UID to be looked
     * @return a add-on information object (could be null)
     */
    public @Nullable AddonInfo getAddonInfo(String uid) {
        return getAddonInfo(uid, null);
    }

    /**
     * Returns the add-on information for the specified add-on UID and locale (language),
     * or {@code null} if no add-on information could be found.
     * <p>
     * If more than one provider provides information for the specified add-on UID and locale,
     * it returns a new {@link AddonInfo} containing merged information from all such providers.
     *
     * @param uid the UID to be looked for
     * @param locale the locale to be used for the add-on information (could be null)
     * @return a localized add-on information object (could be null)
     */
    public @Nullable AddonInfo getAddonInfo(String uid, @Nullable Locale locale) {
        return addonInfoProviders.stream().map(p -> p.getAddonInfo(uid, locale)).filter(Objects::nonNull)
                .collect(Collectors.toMap(a -> a.getUID(), Function.identity(), mergeAddonInfos)).get(uid);
    }

    /**
     * A {@link BinaryOperator} to merge the field values from two {@link AddonInfo} objects into a third such object.
     * <p>
     * This merge strategy enables multiple providers to contribute complementary information about the same add-on.
     * The merge follows these precedence rules:
     * <ol>
     * <li><b>Scalar fields:</b> Non-blank/non-null values from 'a' take precedence over 'b', except for default
     * values</li>
     * <li><b>Collection fields:</b> Union of both collections (countries, discoveryMethods)</li>
     * <li><b>Default value override:</b> If 'a' contains a default-generated value and 'b' has a custom value,
     * 'b' takes precedence</li>
     * </ol>
     * <p>
     * Example: If provider A sets description="Custom description" and provider B sets description="", the result
     * will use "Custom description" from A.
     *
     * @param a the first {@link AddonInfo} (higher priority, could be null)
     * @param b the second {@link AddonInfo} (lower priority, could be null)
     * @return a new {@link AddonInfo} containing the combined field values (could be null)
     */
    private static BinaryOperator<@Nullable AddonInfo> mergeAddonInfos = (a, b) -> {
        // Handle null cases - return the non-null object
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        }

        // Start with all fields from 'a' as the base
        AddonInfo.Builder builder = AddonInfo.builder(a);

        // Merge description: prefer non-blank description from 'a', fallback to 'b'
        if (a.getDescription().isBlank()) {
            builder.withDescription(b.getDescription());
        }

        // Merge connection: prefer non-null value from 'a', fallback to 'b'
        if (a.getConnection() == null && b.getConnection() != null) {
            builder.withConnection(b.getConnection());
        }

        // Merge countries: union of both sets (no duplicates)
        Set<String> countries = new HashSet<>(a.getCountries());
        countries.addAll(b.getCountries());
        if (!countries.isEmpty()) {
            builder.withCountries(countries.stream().toList());
        }

        // Merge configDescriptionURI: prefer non-null/non-empty from 'a', fallback to 'b'
        String aConfigDescriptionURI = a.getConfigDescriptionURI();
        if (aConfigDescriptionURI == null || aConfigDescriptionURI.isEmpty() && b.getConfigDescriptionURI() != null) {
            builder.withConfigDescriptionURI(b.getConfigDescriptionURI());
        }

        // Merge sourceBundle: prefer non-null value from 'a', fallback to 'b'
        if (a.getSourceBundle() == null && b.getSourceBundle() != null) {
            builder.withSourceBundle(b.getSourceBundle());
        }

        // Merge serviceId: if 'a' has default value but 'b' has custom value, prefer 'b'
        String defaultServiceId = a.getType() + "." + a.getId();
        if (defaultServiceId.equals(a.getServiceId()) && !defaultServiceId.equals(b.getServiceId())) {
            builder.withServiceId(b.getServiceId());
        }

        // Merge UID: if 'a' has default value but 'b' has custom value, prefer 'b'
        String defaultUID = a.getType() + Addon.ADDON_SEPARATOR + a.getId();
        if (defaultUID.equals(a.getUID()) && !defaultUID.equals(b.getUID())) {
            builder.withUID(b.getUID());
        }

        // Merge discoveryMethods: union of both sets (no duplicates)
        Set<AddonDiscoveryMethod> discoveryMethods = new HashSet<>(a.getDiscoveryMethods());
        discoveryMethods.addAll(b.getDiscoveryMethods());
        if (!discoveryMethods.isEmpty()) {
            builder.withDiscoveryMethods(discoveryMethods.stream().toList());
        }

        return builder.build();
    };

    /**
     * Returns all add-on information this registry contains.
     *
     * @return a set of all add-on information this registry contains (not null, could be empty)
     */
    public Set<AddonInfo> getAddonInfos() {
        return getAddonInfos(null);
    }

    /**
     * Returns all add-on information in the specified locale (language) this registry contains.
     *
     * @param locale the locale to be used for the add-on information (could be null)
     * @return a localized set of all add-on information this registry contains
     *         (not null, could be empty)
     */
    public Set<AddonInfo> getAddonInfos(@Nullable Locale locale) {
        return addonInfoProviders.stream().map(provider -> provider.getAddonInfos(locale)).flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}

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
package org.openhab.core.addon.eclipse.internal;

import static java.util.Map.entry;
import static org.openhab.core.addon.AddonType.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonInfoRegistry;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.config.core.ConfigurableService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Eclipse-based implementation of {@link AddonService} for development and debugging purposes.
 *
 * <p>
 * This service is designed to work within Eclipse IDE during development. It automatically discovers
 * and exposes installed OSGi bundles as addons by inspecting their symbolic names and metadata.
 * Unlike production addon services, this implementation does not support addon installation or
 * uninstallation operations.
 * </p>
 *
 * <p>
 * <b>Bundle Discovery Strategy:</b><br>
 * Bundles are identified as addons if they meet the following criteria:
 * <ul>
 * <li>Symbolic name starts with "org.openhab."</li>
 * <li>Bundle state is ACTIVE</li>
 * <li>Symbolic name follows the pattern: org.openhab.{type}.{id}</li>
 * <li>Type segment matches a known addon type (binding, automation, etc.)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Example:</b><br>
 * Bundle with symbolic name "org.openhab.binding.hue" will be discovered as:
 * <ul>
 * <li>Type: binding</li>
 * <li>ID: hue</li>
 * <li>UID: eclipse:binding:hue</li>
 * </ul>
 * </p>
 *
 * @author Wouter Born - Initial contribution
 */
@Component(name = "org.openhab.addons")
@NonNullByDefault
@ConfigurableService(category = "system", label = "Add-on Management", description_uri = EclipseAddonService.CONFIG_URI)
public class EclipseAddonService implements AddonService {

    public static final String CONFIG_URI = "system:addons";

    private static final String SERVICE_ID = "eclipse";
    private static final String ADDON_ID_PREFIX = SERVICE_ID + ":";

    private static final String ADDONS_CONTENT_TYPE = "application/vnd.openhab.bundle";
    private static final String ADDONS_AUTHOR = "openHAB";
    private static final String BUNDLE_SYMBOLIC_NAME_PREFIX = "org.openhab.";

    // Constants for symbolic name parsing
    // Expected format: org.openhab.{type}.{id}
    // Example: org.openhab.binding.hue -> segments[0]="org", [1]="openhab", [2]="binding", [3]="hue"
    private static final int SYMBOLIC_NAME_MIN_SEGMENTS = 4;
    private static final int SYMBOLIC_NAME_TYPE_INDEX = 2;
    private static final int SYMBOLIC_NAME_ID_INDEX = 3;

    /**
     * Maps addon types to their corresponding bundle name segments.
     * This mapping defines how addon types (e.g., "binding") are represented
     * in OSGi bundle symbolic names (e.g., "org.openhab.binding.xxx").
     */
    private static final Map<String, String> ADDON_BUNDLE_TYPE_MAP = Map.ofEntries(
            entry(AUTOMATION.getId(), "automation"), //
            entry(BINDING.getId(), "binding"), //
            entry(MISC.getId(), "io"), // Note: MISC type uses "io" in bundle names
            entry(PERSISTENCE.getId(), "persistence"), //
            entry(TRANSFORMATION.getId(), "transform"), // Note: shortened to "transform"
            entry(UI.getId(), "ui"), //
            entry(VOICE.getId(), "voice"));

    /**
     * Reverse mapping: bundle name segment to addon type ID.
     * Dynamically constructed from ADDON_BUNDLE_TYPE_MAP to ensure consistency.
     */
    private static final Map<String, String> BUNDLE_ADDON_TYPE_MAP = ADDON_BUNDLE_TYPE_MAP.entrySet().stream()
            .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

    private static final String DOCUMENTATION_URL_PREFIX = "https://www.openhab.org/addons/";

    /**
     * URL format templates for addon documentation.
     * Each addon type has a specific URL pattern on the openHAB website.
     * The %s placeholder is replaced with the addon ID (e.g., "hue" for Hue binding).
     */
    private static final Map<String, String> DOCUMENTATION_URL_FORMATS = Map.ofEntries(
            entry(AUTOMATION.getId(), DOCUMENTATION_URL_PREFIX + "automation/%s/"), //
            entry(BINDING.getId(), DOCUMENTATION_URL_PREFIX + "bindings/%s/"), //
            entry(MISC.getId(), DOCUMENTATION_URL_PREFIX + "integrations/%s/"), //
            entry(PERSISTENCE.getId(), DOCUMENTATION_URL_PREFIX + "persistence/%s/"), //
            entry(TRANSFORMATION.getId(), DOCUMENTATION_URL_PREFIX + "transformations/%s/"), //
            entry(UI.getId(), DOCUMENTATION_URL_PREFIX + "ui/%s/"), //
            entry(VOICE.getId(), DOCUMENTATION_URL_PREFIX + "voice/%s/"));

    private final BundleContext bundleContext;
    private final AddonInfoRegistry addonInfoRegistry;

    /**
     * Activates the Eclipse addon service.
     *
     * @param bundleContext the OSGi bundle context used to query installed bundles
     * @param addonInfoRegistry registry for retrieving additional addon metadata
     */
    @Activate
    public EclipseAddonService(BundleContext bundleContext, @Reference AddonInfoRegistry addonInfoRegistry) {
        this.bundleContext = bundleContext;
        this.addonInfoRegistry = addonInfoRegistry;
    }

    /**
     * Deactivates the Eclipse addon service.
     * This implementation is empty because the service uses OSGi bundle tracking,
     * which is automatically managed by the framework. No explicit cleanup is required.
     */
    @Deactivate
    protected void deactivate() {
        // No cleanup needed - OSGi manages bundle lifecycle automatically
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return SERVICE_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Eclipse Add-on Service";
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This implementation is a no-op because the Eclipse addon service automatically
     * discovers addons by querying the OSGi bundle context. The addon list is always
     * current and does not require explicit refresh. Bundle installation/uninstallation
     * is handled by the OSGi framework.
     * </p>
     */
    @Override
    public void refreshSource() {
        // No refresh needed - addons are discovered dynamically from OSGi bundle context
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Not supported:</b> The Eclipse addon service is designed for development/debugging
     * and does not support programmatic installation of addons. Addons are installed through
     * the Eclipse IDE's bundle management or OSGi console.
     * </p>
     *
     * @throws UnsupportedOperationException always, as installation is not supported
     */
    @Override
    public void install(String id) {
        throw new UnsupportedOperationException(getName() + " does not support installing add-ons. "
                + "Use Eclipse IDE or OSGi console to install bundles.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Not supported:</b> The Eclipse addon service is designed for development/debugging
     * and does not support programmatic uninstallation of addons. Addons are uninstalled through
     * the Eclipse IDE's bundle management or OSGi console.
     * </p>
     *
     * @throws UnsupportedOperationException always, as uninstallation is not supported
     */
    @Override
    public void uninstall(String id) {
        throw new UnsupportedOperationException(getName() + " does not support uninstalling add-ons. "
                + "Use Eclipse IDE or OSGi console to uninstall bundles.");
    }

    /**
     * Determines if a bundle qualifies as an openHAB addon.
     *
     * <p>
     * A bundle is considered an addon if it meets all of the following criteria:
     * <ol>
     * <li>Symbolic name starts with "org.openhab."</li>
     * <li>Bundle is in ACTIVE state</li>
     * <li>Symbolic name has at least 4 segments (org.openhab.{type}.{id})</li>
     * <li>The type segment (index 2) matches a known addon type</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Examples:</b><br>
     * ✓ "org.openhab.binding.hue" (ACTIVE) → true<br>
     * ✓ "org.openhab.automation.pidcontroller" (ACTIVE) → true<br>
     * ✗ "org.openhab.core.addon" → false (not a type segment)<br>
     * ✗ "org.openhab.binding.mqtt" (RESOLVED, not ACTIVE) → false<br>
     * ✗ "com.example.binding.custom" → false (wrong prefix)
     * </p>
     *
     * @param bundle the OSGi bundle to check
     * @return true if the bundle is a valid openHAB addon, false otherwise
     */
    private boolean isAddon(Bundle bundle) {
        String symbolicName = bundle.getSymbolicName();

        // Quick pre-checks before parsing
        if (!symbolicName.startsWith(BUNDLE_SYMBOLIC_NAME_PREFIX) || bundle.getState() != Bundle.ACTIVE) {
            return false;
        }

        // Parse symbolic name into segments
        String[] segments = symbolicName.split("\\.");

        // Validate format: must have at least org.openhab.{type}.{id}
        if (segments.length < SYMBOLIC_NAME_MIN_SEGMENTS) {
            return false;
        }

        // Check if the type segment is a recognized addon type
        String bundleType = segments[SYMBOLIC_NAME_TYPE_INDEX];
        return ADDON_BUNDLE_TYPE_MAP.containsValue(bundleType);
    }

    /**
     * Transforms an OSGi bundle into an Addon object with metadata.
     *
     * <p>
     * This method performs the following steps:
     * <ol>
     * <li>Parses the bundle symbolic name to extract type and ID</li>
     * <li>Creates an Addon with basic metadata (version, author, installation status)</li>
     * <li>Attempts to enrich with additional metadata from AddonInfoRegistry</li>
     * <li>Generates documentation link based on addon type</li>
     * <li>Configures logger packages for the addon</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Example Transformation:</b><br>
     * Bundle: symbolic name = "org.openhab.binding.hue", version = "4.0.0"<br>
     * Result: Addon with uid = "eclipse:binding:hue", type = "binding", id = "hue"
     * </p>
     *
     * @param bundle the OSGi bundle to transform
     * @param locale the locale for localized metadata (may be null)
     * @return an Addon object representing the bundle
     */
    private Addon getAddon(Bundle bundle, @Nullable Locale locale) {
        String symbolicName = bundle.getSymbolicName();
        String[] segments = symbolicName.split("\\.");

        // Extract type and ID from symbolic name
        // Example: "org.openhab.binding.hue" → type="binding", name="hue"
        String bundleType = segments[SYMBOLIC_NAME_TYPE_INDEX]; // e.g., "binding"
        String type = Objects.requireNonNull(BUNDLE_ADDON_TYPE_MAP.get(bundleType),
                "Unknown bundle type: " + bundleType);
        String name = segments[SYMBOLIC_NAME_ID_INDEX]; // e.g., "hue"

        // Construct unique addon ID (e.g., "binding:hue")
        String uid = type + Addon.ADDON_SEPARATOR + name;

        // Build addon with core metadata
        Addon.Builder addon = Addon.create(ADDON_ID_PREFIX + uid) // e.g., "eclipse:binding:hue"
                .withType(type) // e.g., "binding"
                .withId(name) // e.g., "hue"
                .withContentType(ADDONS_CONTENT_TYPE) // "application/vnd.openhab.bundle"
                .withVersion(bundle.getVersion().toString()) // OSGi bundle version
                .withAuthor(ADDONS_AUTHOR, true) // "openHAB" (verified)
                .withInstalled(true); // Always true for Eclipse-discovered bundles

        // Attempt to enrich with additional metadata from registry
        AddonInfo addonInfo = addonInfoRegistry.getAddonInfo(uid, locale);

        if (addonInfo != null) {
            // Enrich with detailed metadata from addon.xml or other sources
            addon = addon.withLabel(addonInfo.getName()).withDescription(addonInfo.getDescription())
                    .withConnection(addonInfo.getConnection()).withCountries(addonInfo.getCountries())
                    .withLink(getDefaultDocumentationLink(type, name))
                    .withConfigDescriptionURI(addonInfo.getConfigDescriptionURI());
        } else {
            // Fallback: use addon ID as label if no metadata available
            addon = addon.withLabel(name).withLink(getDefaultDocumentationLink(type, name));
        }

        // Configure logger packages for this addon
        addon.withLoggerPackages(List.of(symbolicName));

        return addon.build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Retrieves all installed addons by scanning the OSGi bundle context for bundles
     * that match the addon criteria. The list is dynamically generated on each call
     * and sorted alphabetically by addon label.
     * </p>
     *
     * <p>
     * <b>Performance Note:</b> This method iterates through all bundles in the OSGi
     * container on every invocation. For better performance in production scenarios,
     * consider using a caching mechanism.
     * </p>
     *
     * @param locale the locale for localized metadata (may be null for default locale)
     * @return a sorted list of all discovered addons
     */
    @Override
    public List<Addon> getAddons(@Nullable Locale locale) {
        return Arrays.stream(bundleContext.getBundles()) //
                .filter(this::isAddon) // Filter to only addon bundles
                .map(bundle -> getAddon(bundle, locale)) // Transform to Addon objects
                .sorted(Comparator.comparing(Addon::getLabel)) // Sort alphabetically by label
                .toList();
    }

    /**
     * Generates the default documentation URL for an addon.
     *
     * <p>
     * The URL format varies by addon type. For example:
     * <ul>
     * <li>Bindings: https://www.openhab.org/addons/bindings/{id}/</li>
     * <li>Transformations: https://www.openhab.org/addons/transformations/{id}/</li>
     * </ul>
     * </p>
     *
     * @param type the addon type (e.g., "binding", "automation")
     * @param name the addon ID (e.g., "hue", "mqtt")
     * @return the documentation URL, or null if no format is defined for the type
     */
    private @Nullable String getDefaultDocumentationLink(String type, String name) {
        String format = DOCUMENTATION_URL_FORMATS.get(type);
        return format == null ? null : String.format(format, name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Retrieves a specific addon by its unique identifier. The UID is expected to have
     * the format "eclipse:{type}:{id}" (e.g., "eclipse:binding:hue").
     * </p>
     *
     * <p>
     * This method reconstructs the expected bundle symbolic name from the UID and searches
     * for a matching bundle in the OSGi container.
     * </p>
     *
     * @param uid the addon unique identifier (format: "eclipse:{type}:{id}")
     * @param locale the locale for localized metadata (may be null)
     * @return the Addon if found, null otherwise
     */
    @Override
    public @Nullable Addon getAddon(String uid, @Nullable Locale locale) {
        // Validate input
        if (uid == null || uid.isBlank()) {
            return null;
        }

        // Remove service prefix: "eclipse:binding:hue" → "binding:hue"
        String id = uid.replaceFirst(ADDON_ID_PREFIX, "");

        // Split into type and name: "binding:hue" → ["binding", "hue"]
        String[] segments = id.split(Addon.ADDON_SEPARATOR);

        // Validate format: must have exactly 2 segments (type and name)
        if (segments.length < 2) {
            return null;
        }

        // Validate that the type is known
        String bundleTypeSegment = ADDON_BUNDLE_TYPE_MAP.get(segments[0]);
        if (bundleTypeSegment == null) {
            return null;
        }

        // Reconstruct bundle symbolic name: "org.openhab.binding.hue"
        String symbolicName = BUNDLE_SYMBOLIC_NAME_PREFIX + bundleTypeSegment + "." + segments[1];

        // Search for bundle with matching symbolic name
        return Arrays.stream(bundleContext.getBundles()) //
                .filter(bundle -> bundle.getSymbolicName().equals(symbolicName)) //
                .filter(this::isAddon) // Verify it's a valid addon
                .map(bundle -> getAddon(bundle, locale)) // Transform to Addon
                .findFirst().orElse(null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Returns the default set of addon types supported by openHAB.
     * This includes: automation, binding, misc, persistence, transformation, ui, and voice.
     * </p>
     *
     * @param locale the locale for localized type names (currently unused)
     * @return the list of supported addon types
     */
    @Override
    public List<AddonType> getTypes(@Nullable Locale locale) {
        return AddonType.DEFAULT_TYPES;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Not supported:</b> The Eclipse addon service does not support resolving addon IDs
     * from extension URIs. This functionality is only relevant for marketplace-based addon
     * services.
     * </p>
     *
     * @param extensionURI the extension URI (ignored)
     * @return always returns null
     */
    @Override
    public @Nullable String getAddonId(URI extensionURI) {
        // Not supported for Eclipse-based development
        // Extension URIs are only relevant for marketplace/remote addon sources
        return null;
    }
}

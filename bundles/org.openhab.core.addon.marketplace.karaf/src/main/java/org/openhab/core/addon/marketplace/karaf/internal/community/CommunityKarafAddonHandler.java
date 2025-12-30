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
package org.openhab.core.addon.marketplace.karaf.internal.community;

import static org.openhab.core.addon.marketplace.MarketplaceConstants.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import org.apache.karaf.kar.KarService;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.OpenHAB;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.marketplace.MarketplaceAddonHandler;
import org.openhab.core.addon.marketplace.MarketplaceHandlerException;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.util.UIDUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MarketplaceAddonHandler} implementation that manages Karaf marketplace add-ons.
 * <p>
 * This handler is responsible for:
 * <ul>
 * <li>Installing KAR (Karaf ARchive) files from marketplace URLs</li>
 * <li>Uninstalling KAR-based add-ons</li>
 * <li>Managing a local cache of downloaded KAR files</li>
 * <li>Re-installing cached KARs on system startup</li>
 * </ul>
 * <p>
 * The handler supports add-on types: automation, binding, misc, persistence, transformation, ui, and voice.
 * <p>
 * Cache structure: {@code <userdata>/marketplace/kar/<addon-id>/<kar-file>.kar}
 * <p>
 * Example: For addon "marketplace:binding:example", the cache path would be:
 * {@code <userdata>/marketplace/kar/binding:example/example-1.0.0.kar}
 *
 * @author Kai Kreuzer - Initial contribution and API
 * @author Yannick Schaus - refactoring
 * @author Jan N. Klug - refactor to support kar files
 */
@Component(immediate = true)
@NonNullByDefault
public class CommunityKarafAddonHandler implements MarketplaceAddonHandler {
    /**
     * Cache directory where downloaded KAR files are stored.
     * Located at: {@code <userdata>/marketplace/kar/}
     */
    private static final Path KAR_CACHE_PATH = Path.of(OpenHAB.getUserDataFolder(), "marketplace", "kar");

    /**
     * Supported addon types that this handler can process.
     * These correspond to openHAB addon categories.
     */
    private static final List<String> SUPPORTED_EXT_TYPES = List.of("automation", "binding", "misc", "persistence",
            "transformation", "ui", "voice");

    /** File extension for Karaf Archive files */
    private static final String KAR_EXTENSION = ".kar";

    /** Expected number of KAR files per addon in cache directory */
    private static final int EXPECTED_KAR_FILE_COUNT = 1;

    /** Prefix used for marketplace addon UIDs */
    private static final String MARKETPLACE_PREFIX = "marketplace:";

    private final Logger logger = LoggerFactory.getLogger(CommunityKarafAddonHandler.class);
    private final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);
    private final KarService karService;

    private boolean isReady = false;

    @Activate
    public CommunityKarafAddonHandler(@Reference KarService karService) {
        this.karService = karService;
        // Schedule asynchronous re-installation of cached KARs to avoid blocking service activation
        scheduler.execute(this::ensureCachedKarsAreInstalled);
    }

    @Override
    public boolean supports(String type, String contentType) {
        return SUPPORTED_EXT_TYPES.contains(type) && KAR_CONTENT_TYPE.equals(contentType);
    }

    /**
     * Returns a stream of KAR file paths from the given addon directory.
     * <p>
     * This method uses a try-with-resources block to list files, then converts to a List
     * and back to a Stream. This pattern ensures the directory stream is properly closed
     * before returning, preventing resource leaks.
     * <p>
     * Only files ending with {@code .kar} extension are included.
     *
     * @param addonDirectory the directory to search for KAR files
     * @return a stream of KAR file paths (filenames only, not full paths)
     * @throws IOException if an I/O error occurs when accessing the directory
     */
    private Stream<Path> karFilesStream(Path addonDirectory) throws IOException {
        if (Files.isDirectory(addonDirectory)) {
            // Convert to list first to close the directory stream before returning
            try (Stream<Path> files = Files.list(addonDirectory)) {
                return files.map(Path::getFileName).filter(path -> path.toString().endsWith(KAR_EXTENSION)).toList()
                        .stream();
            }
        }
        return Stream.empty();
    }

    /**
     * Extracts the KAR repository name from a KAR file path.
     * <p>
     * The repository name is the filename without the {@code .kar} extension.
     * <p>
     * Example: {@code example-1.0.0.kar} → {@code example-1.0.0}
     *
     * @param path the path to the KAR file
     * @return the repository name (filename without extension)
     */
    private String pathToKarRepoName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - KAR_EXTENSION.length());
    }

    /**
     * Checks if an addon is currently installed by verifying:
     * <ol>
     * <li>The addon has a cached KAR file in the local cache directory</li>
     * <li>The KAR repository is registered with the Karaf KarService</li>
     * </ol>
     *
     * @param addonId the addon ID to check (e.g., "marketplace:binding:example")
     * @return {@code true} if the addon is installed, {@code false} otherwise
     */
    @Override
    @SuppressWarnings("null")
    public boolean isInstalled(String addonId) {
        try {
            Path addonDirectory = getAddonCacheDirectory(addonId);
            List<String> repositories = karService.list();
            return karFilesStream(addonDirectory).findFirst().map(this::pathToKarRepoName).map(repositories::contains)
                    .orElse(false);
        } catch (IOException e) {
            logger.warn("Failed to determine installation status for '{}' due to I/O error: {}", addonId,
                    e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to determine installation status for '{}': {}", addonId, e.getMessage(), e);
        }

        return false;
    }

    /**
     * Installs a marketplace addon by downloading its KAR file and registering it with Karaf.
     * <p>
     * Installation process:
     * <ol>
     * <li>Extract download URL from addon properties</li>
     * <li>Download KAR file to local cache</li>
     * <li>Install KAR using KarService</li>
     * </ol>
     *
     * @param addon the addon to install (must contain {@code KAR_DOWNLOAD_URL_PROPERTY})
     * @throws MarketplaceHandlerException if installation fails (invalid URL, download error, or installation error)
     */
    @Override
    public void install(Addon addon) throws MarketplaceHandlerException {
        String addonUid = addon.getUid();
        URL sourceUrl;
        try {
            String urlString = (String) addon.getProperties().get(KAR_DOWNLOAD_URL_PROPERTY);
            if (urlString == null || urlString.isBlank()) {
                throw new MarketplaceHandlerException(
                        "Addon '" + addonUid + "' is missing required property: " + KAR_DOWNLOAD_URL_PROPERTY, null);
            }
            sourceUrl = (new URI(urlString)).toURL();
        } catch (IllegalArgumentException | MalformedURLException | URISyntaxException e) {
            throw new MarketplaceHandlerException(
                    "Addon '" + addonUid + "' has malformed download URL: " + e.getMessage(), e);
        }
        addKarToCache(addonUid, sourceUrl);
        installFromCache(addonUid);
    }

    /**
     * Uninstalls a marketplace addon by removing it from Karaf and deleting cached files.
     * <p>
     * Uninstallation process:
     * <ol>
     * <li>Locate cached KAR file(s) for the addon</li>
     * <li>Uninstall KAR repository from Karaf (if registered)</li>
     * <li>Delete KAR file(s) from cache</li>
     * <li>Remove addon cache directory</li>
     * </ol>
     *
     * @param addon the addon to uninstall
     * @throws MarketplaceHandlerException if uninstallation fails
     */
    @Override
    public void uninstall(Addon addon) throws MarketplaceHandlerException {
        String addonUid = addon.getUid();
        try {
            Path addonPath = getAddonCacheDirectory(addonUid);
            List<String> repositories = karService.list();
            // Uninstall each KAR file found in the addon cache directory
            for (Path path : karFilesStream(addonPath).toList()) {
                String karRepoName = pathToKarRepoName(path);
                if (repositories.contains(karRepoName)) {
                    karService.uninstall(karRepoName);
                    logger.debug("Uninstalled KAR repository '{}' for addon '{}'", karRepoName, addonUid);
                }
                Files.delete(addonPath.resolve(path));
            }
            Files.delete(addonPath);
            logger.info("Successfully uninstalled addon '{}' and removed cache", addonUid);
        } catch (IOException e) {
            throw new MarketplaceHandlerException(
                    "Failed to uninstall addon '" + addonUid + "' due to I/O error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MarketplaceHandlerException("Failed to uninstall addon '" + addonUid + "': " + e.getMessage(), e);
        }
    }

    /**
     * Downloads a KAR file from a remote source and stores it in the local cache.
     * <p>
     * The KAR file is saved to: {@code <cache>/<addon-id>/<filename>.kar}
     * <p>
     * If a file already exists at the target location, it will be replaced.
     *
     * @param addonId the addon ID (used to determine cache subdirectory)
     * @param sourceUrl the URL where the KAR file can be downloaded
     * @throws MarketplaceHandlerException if download or file operations fail
     */
    private void addKarToCache(String addonId, URL sourceUrl) throws MarketplaceHandlerException {
        try {
            String fileName = new File(sourceUrl.toURI().getPath()).getName();
            Path addonFile = getAddonCacheDirectory(addonId).resolve(fileName);
            Files.createDirectories(addonFile.getParent());
            // Use try-with-resources to ensure InputStream is properly closed
            try (InputStream source = sourceUrl.openStream()) {
                Files.copy(source, addonFile, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Downloaded KAR file for addon '{}' from '{}' to '{}'", addonId, sourceUrl, addonFile);
            }
        } catch (IOException e) {
            throw new MarketplaceHandlerException(
                    "Failed to download KAR for addon '" + addonId + "' from '" + sourceUrl + "': " + e.getMessage(),
                    e);
        } catch (URISyntaxException e) {
            throw new MarketplaceHandlerException(
                    "Invalid URL format for addon '" + addonId + "': " + sourceUrl + " - " + e.getMessage(), e);
        }
    }

    /**
     * Installs a KAR file from the local cache using the Karaf KarService.
     * <p>
     * This method expects exactly one KAR file to exist in the addon's cache directory.
     * If zero or multiple KAR files are found, an exception is thrown.
     *
     * @param addonId the addon ID (used to locate the cache directory)
     * @throws MarketplaceHandlerException if installation fails or cache is invalid
     */
    private void installFromCache(String addonId) throws MarketplaceHandlerException {
        Path addonPath = getAddonCacheDirectory(addonId);
        if (!Files.isDirectory(addonPath)) {
            throw new MarketplaceHandlerException(
                    "Cache directory does not exist for addon '" + addonId + "': " + addonPath, null);
        }

        try (Stream<Path> files = Files.list(addonPath)) {
            List<Path> karFiles = files.toList();
            if (karFiles.size() != EXPECTED_KAR_FILE_COUNT) {
                throw new MarketplaceHandlerException(
                        "Expected exactly " + EXPECTED_KAR_FILE_COUNT + " KAR file in cache for addon '" + addonId
                                + "', but found " + karFiles.size() + " in: " + addonPath,
                        null);
            }
            try {
                Path karFile = karFiles.getFirst();
                karService.install(karFile.toUri(), false);
                logger.info("Successfully installed KAR '{}' for addon '{}'", karFile.getFileName(), addonId);
            } catch (Exception e) {
                throw new MarketplaceHandlerException(
                        "Failed to install KAR for addon '" + addonId + "' from cache: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new MarketplaceHandlerException(
                    "Failed to list files in cache directory for addon '" + addonId + "': " + addonPath, e);
        }
    }

    /**
     * Re-installs all cached KAR files that are not currently installed in Karaf.
     * <p>
     * This method is called asynchronously during service activation to ensure
     * previously installed marketplace addons are restored after a system restart.
     * <p>
     * The method scans the cache directory for addon subdirectories, checks their
     * installation status, and attempts to re-install any that are missing.
     * <p>
     * After completion, sets {@code isReady = true} to indicate the handler is ready.
     */
    private void ensureCachedKarsAreInstalled() {
        logger.debug("Starting re-installation check for cached marketplace KARs");
        if (Files.isDirectory(KAR_CACHE_PATH)) {
            try (Stream<Path> files = Files.list(KAR_CACHE_PATH)) {
                files.filter(Files::isDirectory).map(this::addonIdFromPath).filter(addonId -> !isInstalled(addonId))
                        .forEach(addonId -> {
                            logger.info("Re-installing missing marketplace KAR: '{}'", addonId);
                            try {
                                installFromCache(addonId);
                            } catch (MarketplaceHandlerException e) {
                                logger.warn("Failed to re-install addon '{}' from cache: {}", addonId, e.getMessage(),
                                        e);
                            }
                        });
                logger.debug("Completed re-installation check for cached marketplace KARs");
            } catch (IOException e) {
                logger.warn("Failed to scan cache directory for re-installation: {}", e.getMessage());
            }
        } else {
            logger.debug("Cache directory does not exist, skipping re-installation: {}", KAR_CACHE_PATH);
        }
        isReady = true;
    }

    /**
     * Converts a cache directory path to an addon ID.
     * <p>
     * The directory name is URL-decoded and prefixed with "marketplace:" if not already present.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code binding:example} → {@code marketplace:binding:example}</li>
     * <li>{@code marketplace:ui:dashboard} → {@code marketplace:ui:dashboard}</li>
     * </ul>
     *
     * @param path the cache directory path
     * @return the addon ID
     */
    private String addonIdFromPath(Path path) {
        String pathName = UIDUtils.decode(path.getFileName().toString());
        return pathName.contains(":") ? pathName : MARKETPLACE_PREFIX + pathName;
    }

    /**
     * Returns the cache directory path for a given addon ID.
     * <p>
     * The addon ID is encoded and the "marketplace:" prefix is removed if present.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code marketplace:binding:example} → {@code <cache>/binding:example/}</li>
     * <li>{@code binding:example} → {@code <cache>/<encoded>/}</li>
     * </ul>
     *
     * @param addonId the addon ID
     * @return the cache directory path for this addon
     */
    private Path getAddonCacheDirectory(String addonId) {
        String dir = addonId.startsWith(MARKETPLACE_PREFIX) ? addonId.replace(MARKETPLACE_PREFIX, "")
                : UIDUtils.encode(addonId);
        return KAR_CACHE_PATH.resolve(dir);
    }

    @Override
    public boolean isReady() {
        return isReady;
    }
}

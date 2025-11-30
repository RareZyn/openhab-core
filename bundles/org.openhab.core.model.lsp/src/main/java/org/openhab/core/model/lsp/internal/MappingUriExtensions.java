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
package org.openhab.core.model.lsp.internal;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtext.ide.server.UriExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UriExtensions} implementation.
 *
 * It takes into account the fact that although language server and client both operate on the same set of files, their
 * file system location might be different due to remote access via SMB, SSH and the like.
 *
 * @author Simon Kaufmann - Initial contribution
 */
@NonNullByDefault
public class MappingUriExtensions extends UriExtensions {

    private final Logger logger = LoggerFactory.getLogger(MappingUriExtensions.class);

    private final String rawConfigFolder;
    private final String serverLocation;

    private @Nullable String clientLocation;

    /**
     * Constructs a new MappingUriExtensions with the specified config folder.
     *
     * @param configFolder the configuration folder path, must not be null or empty
     */
    public MappingUriExtensions(String configFolder) {
        if (configFolder == null || configFolder.isEmpty()) {
            throw new IllegalArgumentException("Config folder cannot be null or empty");
        }
        this.rawConfigFolder = configFolder;
        this.serverLocation = calcServerLocation(configFolder);
        logger.debug("The language server is using '{}' as its workspace", serverLocation);
    }

    /**
     * Calculates the server location from the config folder path.
     *
     * @param configFolder the configuration folder path, must not be null
     * @return the server location URI string
     */
    protected String calcServerLocation(String configFolder) {
        if (configFolder == null) {
            throw new IllegalArgumentException("Config folder cannot be null");
        }
        Path configPath = Path.of(configFolder);
        Path absoluteConfigPath = configPath.toAbsolutePath();
        java.net.URI configPathURI = absoluteConfigPath.toUri();
        return removeTrailingSlash(configPathURI.toString());
    }

    /**
     * Converts a path with scheme to a URI, handling client-server path mapping.
     *
     * @param pathWithScheme the path with scheme (e.g., "file:///path"), must not be null
     * @return the mapped URI
     */
    @Override
    public URI toUri(@NonNullByDefault({}) String pathWithScheme) {
        if (pathWithScheme == null || pathWithScheme.isEmpty()) {
            throw new IllegalArgumentException("Path with scheme cannot be null or empty");
        }
        String decodedPathWithScheme = URLDecoder.decode(pathWithScheme, StandardCharsets.UTF_8);
        String localClientLocation = clientLocation;
        if (localClientLocation != null && decodedPathWithScheme.startsWith(localClientLocation)) {
            return map(decodedPathWithScheme);
        }

        localClientLocation = clientLocation = guessClientPath(decodedPathWithScheme);
        if (localClientLocation != null) {
            logger.debug("Identified client workspace as '{}'", localClientLocation);
            return map(decodedPathWithScheme);
        }

        clientLocation = pathWithScheme;

        logger.debug("Path mapping could not be done for '{}', leaving it untouched", pathWithScheme);
        java.net.URI javaNetUri = java.net.URI.create(pathWithScheme);
        return URI.createURI(toPathAsInXtext212(javaNetUri));
    }

    /**
     * Converts a URI to a URI string, mapping server paths to client paths if needed.
     *
     * @param uri the URI to convert, must not be null
     * @return the URI string representation
     */
    @Override
    public String toUriString(@NonNullByDefault({}) URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        if (clientLocation == null) {
            return uri.toString();
        }
        return mapToClientPath(uri.toString());
    }

    /**
     * Converts a java.net.URI to a URI string, mapping server paths to client paths if needed.
     *
     * @param uri the java.net.URI to convert, must not be null
     * @return the URI string representation
     */
    @Override
    public String toUriString(@NonNullByDefault({}) java.net.URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        return toUriString(URI.createURI(uri.toString()));
    }

    private String mapToClientPath(String pathWithScheme) {
        String clientLocation = this.clientLocation;
        String uriString = clientLocation == null ? serverLocation
                : pathWithScheme.replace(serverLocation, clientLocation);
        String clientPath = toPathAsInXtext212(java.net.URI.create(uriString));
        logger.trace("Mapping server path {} to client path {}", pathWithScheme, clientPath);
        return clientPath;
    }

    /**
     * Removes a trailing slash from a path string if present.
     *
     * @param path the path string, must not be null
     * @return the path without trailing slash
     */
    protected final String removeTrailingSlash(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        } else {
            return path;
        }
    }

    /**
     * Guess the client path.
     *
     * It works as follows: It starts with replacing the full clients path with the path of the config folder.
     * In the next iteration it shortens the path to be replaced by one subfolder.
     * It repeats that until the resulting filename exists.
     *
     * @param pathWithScheme the filename as coming from the client
     * @return the substring which needs to be replaced with the runtime's config folder path
     */
    protected @Nullable String guessClientPath(String pathWithScheme) {
        if (isPointingToConfigFolder(pathWithScheme)) {
            return removeTrailingSlash(pathWithScheme);
        } else if (isFolder(pathWithScheme)) {
            return removeTrailingSlash(pathWithScheme);
        }

        String currentPath = pathWithScheme;
        int nextIndex = getLastPathSegmentIndex(currentPath);
        while (nextIndex > -1) {
            currentPath = currentPath.substring(0, nextIndex);
            java.net.URI uri = toURI(pathWithScheme, currentPath);
            File realFile = new File(uri);
            if (realFile.exists()) {
                return currentPath;
            }

            nextIndex = getLastPathSegmentIndex(currentPath);
        }

        return null;
    }

    /**
     * Checks if the current path represents a folder (no file extension).
     *
     * @param currentPath the path to check, must not be null
     * @return true if the path represents a folder, false otherwise
     */
    private boolean isFolder(String currentPath) {
        if (currentPath == null) {
            return false;
        }
        int lastIndex = getLastPathSegmentIndex(currentPath);
        if (lastIndex < 0) {
            return true;
        }
        return !currentPath.substring(lastIndex).contains(".");
    }

    /**
     * Checks if the current path points to the config folder.
     *
     * @param currentPath the path to check, must not be null
     * @return true if the path ends with the config folder, false otherwise
     */
    private boolean isPointingToConfigFolder(String currentPath) {
        if (currentPath == null || rawConfigFolder == null) {
            return false;
        }
        return currentPath.endsWith("/" + rawConfigFolder);
    }

    /**
     * Gets the index of the last path segment separator.
     *
     * @param currentPath the path to analyze, must not be null
     * @return the index of the last "/", or -1 if not found
     */
    private int getLastPathSegmentIndex(String currentPath) {
        if (currentPath == null) {
            return -1;
        }
        return removeTrailingSlash(currentPath).lastIndexOf("/");
    }

    /**
     * Maps a path with scheme from client location to server location.
     *
     * @param pathWithScheme the path with scheme to map, must not be null
     * @return the mapped URI
     */
    private URI map(String pathWithScheme) {
        if (pathWithScheme == null) {
            throw new IllegalArgumentException("Path with scheme cannot be null");
        }
        java.net.URI javaNetUri = toURI(pathWithScheme, clientLocation);
        logger.trace("Going to map path {}", javaNetUri);
        URI ret = URI.createURI(toPathAsInXtext212(javaNetUri));
        logger.trace("Mapped path {} to {}", pathWithScheme, ret);
        return ret;
    }

    /**
     * Converts a path with scheme to a java.net.URI, replacing client location with server location.
     *
     * @param pathWithScheme the path with scheme, must not be null
     * @param currentPath the current path to replace, may be null
     * @return the java.net.URI
     */
    private java.net.URI toURI(String pathWithScheme, @Nullable String currentPath) {
        if (pathWithScheme == null) {
            throw new IllegalArgumentException("Path with scheme cannot be null");
        }
        String path = currentPath == null ? pathWithScheme : pathWithScheme.replace(currentPath, serverLocation);
        return java.net.URI.create(path);
    }

    private String toPathAsInXtext212(java.net.URI uri) {
        // org.eclipse.xtext.ide.server.UriExtensions:
        // In Xtext 2.14 the method "String toPath(java.netURI)" has been deprecated but still exist.
        // It delegate the logic internally to the new method "String toUriString(java.net.URI uri)".
        // That new method seems to return a different result for folder / directories with respect to
        // the present / absent of a trailing slash.

        // The old logic removes trailing slashes if it has been present in the input.
        // The new logic keeps trailing slashes if it has been present in the input.

        // input: file:///d/
        // output old: file:///d
        // output new: file:///d

        // input: file:///d/
        // output old: file:///d
        // output new: file:///d/

        // We use this method now to keep the old behavior.
        return Path.of(uri).toUri().toString();
    }
}

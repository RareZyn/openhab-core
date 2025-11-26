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
package org.openhab.core;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.framework.FrameworkUtil;

/**
 * Some core static methods that provide information about the running openHAB instance.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class OpenHAB {

    /** The program argument name for setting the runtime directory path */
    public static final String RUNTIME_DIR_PROG_ARGUMENT = "openhab.runtime";

    /** The program argument name for setting the user data directory path */
    public static final String USERDATA_DIR_PROG_ARGUMENT = "openhab.userdata";

    /** The program argument name for setting the main config directory path */
    public static final String CONFIG_DIR_PROG_ARGUMENT = "openhab.conf";

    /** The default runtime directory name */
    public static final String DEFAULT_RUNTIME_FOLDER = "runtime";

    /** The default main configuration directory name */
    public static final String DEFAULT_CONFIG_FOLDER = "conf";

    /** The default user data directory name */
    public static final String DEFAULT_USERDATA_FOLDER = "userdata";

    /** The property to recognize a service instance created by a service factory */
    public static final String SERVICE_CONTEXT = "openhab.servicecontext";

    /** The property to separate service PIDs from their contexts */
    public static final String SERVICE_CONTEXT_MARKER = "#";

    /** the service pid used for the definition of the base package and add-ons */
    public static final String ADDONS_SERVICE_PID = "org.openhab.addons";

    /** the configuration parameter name used for the base package */
    public static final String CFG_PACKAGE = "package";

    /**
     * Returns the current openHAB version, retrieving the information from the core bundle version.
     * <p>
     * The version is derived from the OSGi bundle version. If the version contains a snapshot qualifier
     * (e.g., "5.1.0.qualifier"), it is automatically removed to produce a clean version string (e.g., "5.1.0").
     * <p>
     * Examples:
     * <ul>
     * <li>"5.1.0" → "5.1.0"</li>
     * <li>"5.1.0.qualifier" → "5.1.0"</li>
     * <li>"5.1.0.202501261234" → "5.1.0"</li>
     * </ul>
     *
     * @return the openHAB runtime version (e.g., "5.1.0")
     */
    public static String getVersion() {
        String versionString = FrameworkUtil.getBundle(OpenHAB.class).getVersion().toString();
        // If the version string contains a "snapshot" qualifier (4 segments), remove the last segment
        if (versionString.chars().filter(ch -> ch == '.').count() == 3) {
            final Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
            String qualifier = substringAfterLast(versionString, ".");
            // Remove qualifier if it's a timestamp or the literal "qualifier"
            if (pattern.matcher(qualifier).matches() || "qualifier".equals(qualifier)) {
                versionString = substringBeforeLast(versionString, ".");
            }
        }
        return versionString;
    }

    /**
     * Provides the build number as it can be found in the version.properties file.
     *
     * @return The build string or "Unknown Build No." if none can be identified.
     */
    public static String buildString() {
        Properties prop = new Properties();
        Path versionFilePath = Path.of(getUserDataFolder(), "etc", "version.properties");
        try (FileInputStream fis = new FileInputStream(versionFilePath.toFile())) {
            prop.load(fis);
            String buildNo = prop.getProperty("build-no");
            if (buildNo != null && !buildNo.isEmpty()) {
                return buildNo;
            }
        } catch (java.io.IOException e) {
            // File not found or not readable - expected in development environments
        } catch (SecurityException e) {
            // File access denied - security manager restrictions
        } catch (IllegalArgumentException e) {
            // Invalid path format - should not occur with hardcoded path
        }
        return "Unknown Build No.";
    }

    /**
     * Returns the runtime folder path name. The runtime folder <code>&lt;openhab-home&gt;/runtime</code> can be
     * overwritten by setting the System property <code>openhab.runtime</code>.
     *
     * @return the runtime folder path name
     */
    public static String getRuntimeFolder() {
        String progArg = System.getProperty(RUNTIME_DIR_PROG_ARGUMENT);
        if (progArg != null) {
            return progArg;
        } else {
            return DEFAULT_RUNTIME_FOLDER;
        }
    }

    /**
     * Returns the configuration folder path name. The main config folder <code>&lt;openhab-home&gt;/conf</code> can be
     * overwritten by setting the System property <code>openhab.conf</code>.
     *
     * @return the configuration folder path name
     */
    public static String getConfigFolder() {
        String progArg = System.getProperty(CONFIG_DIR_PROG_ARGUMENT);
        if (progArg != null) {
            return progArg;
        } else {
            return DEFAULT_CONFIG_FOLDER;
        }
    }

    /**
     * Returns the user data folder path name. The main user data folder <code>&lt;openhab-home&gt;/userdata</code> can
     * be overwritten by setting the System property <code>openhab.userdata</code>.
     *
     * @return the user data folder path name
     */
    public static String getUserDataFolder() {
        String progArg = System.getProperty(USERDATA_DIR_PROG_ARGUMENT);
        if (progArg != null) {
            return progArg;
        } else {
            return DEFAULT_USERDATA_FOLDER;
        }
    }

    /**
     * Extracts the substring after the last occurrence of a separator.
     *
     * @param str the string to search in
     * @param separator the separator string to find
     * @return the substring after the last separator, or empty string if separator not found or at the end
     */
    private static String substringAfterLast(String str, String separator) {
        int index = str.lastIndexOf(separator);
        return index == -1 || index == str.length() - separator.length() ? ""
                : str.substring(index + separator.length());
    }

    /**
     * Extracts the substring before the last occurrence of a separator.
     *
     * @param str the string to search in
     * @param separator the separator string to find
     * @return the substring before the last separator, or the original string if separator not found
     */
    private static String substringBeforeLast(String str, String separator) {
        int index = str.lastIndexOf(separator);
        return index == -1 ? str : str.substring(0, index);
    }
}

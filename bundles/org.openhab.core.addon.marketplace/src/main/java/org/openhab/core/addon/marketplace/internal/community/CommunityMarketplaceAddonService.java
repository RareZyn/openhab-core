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
package org.openhab.core.addon.marketplace.internal.community;

import static org.openhab.core.addon.Addon.CODE_MATURITY_LEVELS;
import static org.openhab.core.addon.marketplace.MarketplaceConstants.*;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonInfoRegistry;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.addon.marketplace.AbstractRemoteAddonService;
import org.openhab.core.addon.marketplace.BundleVersion;
import org.openhab.core.addon.marketplace.MarketplaceAddonHandler;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseCategoryResponseDTO;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseCategoryResponseDTO.DiscoursePosterInfo;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseCategoryResponseDTO.DiscourseTopicItem;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseCategoryResponseDTO.DiscourseUser;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseTopicResponseDTO;
import org.openhab.core.addon.marketplace.internal.community.model.DiscourseTopicResponseDTO.DiscoursePostLink;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.storage.StorageService;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an {@link org.openhab.core.addon.AddonService} retrieving posts from the
 * openHAB Community forum (Discourse platform).
 *
 * <p>
 * <b>How it works:</b>
 * <ol>
 * <li>Fetches posts from the Marketplace category on community.openhab.org</li>
 * <li>Parses Discourse JSON API responses to extract addon metadata</li>
 * <li>Filters posts by "published" tag (unless showUnpublished is enabled)</li>
 * <li>Determines addon type from Discourse category (bundles, rule templates, UI widgets, etc.)</li>
 * <li>Extracts version compatibility from post titles (e.g., "My Addon [3.0.0,4.0.0)")</li>
 * <li>Delegates installation to appropriate handlers based on content type</li>
 * </ol>
 *
 * <p>
 * <b>Supported addon types:</b>
 * <ul>
 * <li>JAR/KAR bundles (category 73)</li>
 * <li>Rule templates (category 74)</li>
 * <li>UI widgets (category 75)</li>
 * <li>Block libraries (category 76)</li>
 * <li>Transformations (category 80)</li>
 * </ul>
 *
 * <p>
 * <b>Configuration properties:</b>
 * <ul>
 * <li>{@code apiKey} - Optional Discourse API key for authenticated requests (increases rate limits)</li>
 * <li>{@code showUnpublished} - Show addons without "published" tag (default: false)</li>
 * <li>{@code enable} - Enable/disable community marketplace (default: true)</li>
 * </ul>
 *
 * @author Yannick Schaus - Initial contribution
 */
@Component(immediate = true, configurationPid = CommunityMarketplaceAddonService.SERVICE_PID, //
        property = Constants.SERVICE_PID + "="
                + CommunityMarketplaceAddonService.SERVICE_PID, service = AddonService.class)
@ConfigurableService(category = "system", label = CommunityMarketplaceAddonService.SERVICE_NAME, description_uri = CommunityMarketplaceAddonService.CONFIG_URI)
@NonNullByDefault
public class CommunityMarketplaceAddonService extends AbstractRemoteAddonService {
    public static final String CODE_CONTENT_SUFFIX = "_content";
    public static final String JSON_CONTENT_PROPERTY = "json" + CODE_CONTENT_SUFFIX;
    public static final String YAML_CONTENT_PROPERTY = "yaml" + CODE_CONTENT_SUFFIX;

    // Network timeout constants (in milliseconds)
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    // constants for the configuration properties
    static final String SERVICE_NAME = "Community Marketplace";
    static final String SERVICE_PID = "org.openhab.marketplace";
    static final String CONFIG_URI = "system:marketplace";
    static final String CONFIG_API_KEY = "apiKey";
    static final String CONFIG_SHOW_UNPUBLISHED_ENTRIES_KEY = "showUnpublished";
    static final String CONFIG_ENABLED_KEY = "enable";

    private static final String COMMUNITY_BASE_URL = "https://community.openhab.org";
    private static final String COMMUNITY_MARKETPLACE_URL = COMMUNITY_BASE_URL + "/c/marketplace/69/l/latest";
    private static final String COMMUNITY_TOPIC_URL = COMMUNITY_BASE_URL + "/t/";
    // More robust pattern supporting various version formats:
    // - Semantic versions: 1.0.0, 2.5.3
    // - Snapshots: 1.0.0-SNAPSHOT, 3.2.1-beta1
    // - Build metadata: 1.0.0+build.123, 2.0.0-rc.1+20241125
    // - Release suffixes: 1.0.0.RELEASE, 1.0.0.Final
    private static final Pattern BUNDLE_NAME_PATTERN = Pattern
            .compile(".*/(.*?)-\\d+\\.\\d+\\.\\d+(?:[.-].*)?\\.(jar|kar)$");

    private static final String SERVICE_ID = "marketplace";
    private static final String ADDON_ID_PREFIX = SERVICE_ID + ":";

    private static final Pattern CODE_MARKUP_PATTERN = Pattern.compile(
            "<pre(?: data-code-wrap=\"[a-z]+\")?><code class=\"lang-(?<lang>[a-z]+)\">(?<content>.*?)</code></pre>",
            Pattern.DOTALL);

    private static final Integer BUNDLES_CATEGORY = 73;
    private static final Integer RULETEMPLATES_CATEGORY = 74;
    private static final Integer UIWIDGETS_CATEGORY = 75;
    private static final Integer BLOCKLIBRARIES_CATEGORY = 76;
    private static final Integer TRANSFORMATIONS_CATEGORY = 80;

    private static final String PUBLISHED_TAG = "published";

    private final Logger logger = LoggerFactory.getLogger(CommunityMarketplaceAddonService.class);

    private @Nullable String apiKey = null;
    private boolean showUnpublished = false;
    private boolean enabled = true;

    @Activate
    public CommunityMarketplaceAddonService(final @Reference EventPublisher eventPublisher,
            @Reference ConfigurationAdmin configurationAdmin, @Reference StorageService storageService,
            @Reference AddonInfoRegistry addonInfoRegistry, Map<String, Object> config) {
        super(eventPublisher, configurationAdmin, storageService, addonInfoRegistry, SERVICE_PID);
        modified(config);
    }

    @Modified
    public void modified(@Nullable Map<String, Object> config) {
        if (config != null) {
            this.apiKey = (String) config.get(CONFIG_API_KEY);
            this.showUnpublished = ConfigParser.valueAsOrElse(config.get(CONFIG_SHOW_UNPUBLISHED_ENTRIES_KEY),
                    Boolean.class, false);
            this.enabled = ConfigParser.valueAsOrElse(config.get(CONFIG_ENABLED_KEY), Boolean.class, true);
            cachedRemoteAddons.invalidateValue();
            refreshSource();
        }
    }

    @Override
    @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE, policy = ReferencePolicy.DYNAMIC)
    protected void addAddonHandler(MarketplaceAddonHandler handler) {
        this.addonHandlers.add(handler);
    }

    @Override
    protected void removeAddonHandler(MarketplaceAddonHandler handler) {
        this.addonHandlers.remove(handler);
    }

    @Override
    public String getId() {
        return SERVICE_ID;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    /**
     * Fetches all available addons from the openHAB Community marketplace.
     *
     * <p>
     * This method performs the following operations:
     * <ol>
     * <li>Fetches paginated topic listings from the Discourse API</li>
     * <li>Filters topics by the "published" tag (unless showUnpublished is enabled)</li>
     * <li>Converts each topic into an {@link Addon} object with metadata</li>
     * <li>Handles network errors gracefully by returning an empty list on failure</li>
     * </ol>
     *
     * <p>
     * <b>Network behavior:</b>
     * <ul>
     * <li>Connect timeout: 10 seconds</li>
     * <li>Read timeout: 30 seconds</li>
     * <li>Supports optional API key authentication for higher rate limits</li>
     * </ul>
     *
     * <p>
     * <b>Error handling:</b> Network failures, timeouts, and JSON parsing errors are logged
     * but do not throw exceptions. The method returns a partial list of successfully parsed addons.
     *
     * @return list of available addons from the community marketplace, or empty list on failure/disabled
     */
    @Override
    protected List<Addon> getRemoteAddons() {
        if (!enabled) {
            return List.of();
        }

        List<Addon> addons = new ArrayList<>();
        try {
            List<DiscourseCategoryResponseDTO> pages = new ArrayList<>();

            URL url = URI.create(COMMUNITY_MARKETPLACE_URL).toURL();
            int pageNb = 1;
            while (url != null) {
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.addRequestProperty("Accept", "application/json");
                if (this.apiKey != null) {
                    connection.addRequestProperty("Api-Key", this.apiKey);
                }

                try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                    DiscourseCategoryResponseDTO parsed = gson.fromJson(reader, DiscourseCategoryResponseDTO.class);
                    if (parsed.topicList.topics.length != 0) {
                        pages.add(parsed);
                    }

                    if (parsed.topicList.moreTopicsUrl != null) {
                        // Discourse URL for next page is wrong
                        url = URI.create(COMMUNITY_MARKETPLACE_URL + "?page=" + pageNb++).toURL();
                    } else {
                        url = null;
                    }
                }
            }

            List<DiscourseUser> users = pages.stream().flatMap(p -> Stream.of(p.users)).toList();
            pages.stream().flatMap(p -> Stream.of(p.topicList.topics))
                    .filter(t -> showUnpublished || List.of(t.tags).contains(PUBLISHED_TAG))
                    .map(t -> Optional.ofNullable(convertTopicItemToAddon(t, users)))
                    .forEach(a -> a.ifPresent(addons::add));
        } catch (java.net.SocketTimeoutException e) {
            logger.warn("Timeout while retrieving marketplace add-ons from '{}': {}", COMMUNITY_MARKETPLACE_URL,
                    e.getMessage());
        } catch (java.io.IOException e) {
            logger.warn("Network error while retrieving marketplace add-ons: {}", e.getMessage());
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Failed to parse JSON response from marketplace API: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving marketplace add-ons: {}", e.getMessage(), e);
        }
        return addons;
    }

    /**
     * Retrieves a specific addon by its unique identifier.
     *
     * <p>
     * <b>Lookup strategy:</b>
     * <ol>
     * <li>Checks if the addon is installed locally (fast path - uses cached data)</li>
     * <li>If not installed and remote is enabled, fetches full details from Discourse API</li>
     * </ol>
     *
     * <p>
     * <b>UID format:</b> Accepts both prefixed ({@code marketplace:123}) and unprefixed ({@code 123})
     * topic IDs. The method normalizes the UID before lookup.
     *
     * <p>
     * <b>Network behavior:</b> If the addon is not installed, this method makes a synchronous
     * HTTP request to fetch the topic details from community.openhab.org.
     *
     * @param uid the addon unique identifier (topic ID with optional "marketplace:" prefix)
     * @param locale the locale (currently unused - Discourse content is primarily English)
     * @return the addon with full details, or null if not found / remote disabled / network error
     */
    @Override
    public @Nullable Addon getAddon(String uid, @Nullable Locale locale) {
        String queryId = uid.startsWith(ADDON_ID_PREFIX) ? uid : ADDON_ID_PREFIX + uid;

        // check if it is an installed add-on (cachedAddons also contains possibly incomplete results from the remote
        // side, we need to retrieve them from Discourse)

        if (installedAddonIds.contains(queryId)) {
            return cachedAddons.stream().filter(e -> queryId.equals(e.getUid())).findAny().orElse(null);
        }

        if (!remoteEnabled()) {
            return null;
        }

        // retrieve from remote
        try {
            URL url = URI.create(COMMUNITY_TOPIC_URL + uid.replace(ADDON_ID_PREFIX, "")).toURL();
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.addRequestProperty("Accept", "application/json");
            if (this.apiKey != null) {
                connection.addRequestProperty("Api-Key", this.apiKey);
            }

            try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                DiscourseTopicResponseDTO parsed = gson.fromJson(reader, DiscourseTopicResponseDTO.class);
                if (parsed == null || parsed.id == null) {
                    logger.warn("Received null or invalid response when fetching addon '{}' from marketplace", uid);
                    return null;
                }
                return convertTopicToAddon(parsed);
            }
        } catch (java.net.SocketTimeoutException e) {
            logger.warn("Timeout while fetching addon '{}' from marketplace: {}", uid, e.getMessage());
            return null;
        } catch (java.io.IOException e) {
            logger.debug("Network error while fetching addon '{}': {}", uid, e.getMessage());
            return null;
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Failed to parse JSON response for addon '{}': {}", uid, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error while fetching addon '{}': {}", uid, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public @Nullable String getAddonId(URI addonURI) {
        String uriString = addonURI.toString();
        if (uriString.startsWith(COMMUNITY_TOPIC_URL)) {
            int separatorIndex = uriString.indexOf("/", COMMUNITY_BASE_URL.length());
            if (separatorIndex > 0) {
                return uriString.substring(0, separatorIndex);
            }
        }
        logger.debug("Could not extract addon ID from URI: {}", uriString);
        return null;
    }

    /**
     * Determines the openHAB addon type from Discourse category and tags.
     *
     * <p>
     * <b>Category mapping:</b>
     * <ul>
     * <li>Category 73 (Bundles) → type from tags (binding, persistence, etc.)</li>
     * <li>Category 74 (Rule Templates) → {@link AddonType#AUTOMATION}</li>
     * <li>Category 75 (UI Widgets) → {@link AddonType#UI}</li>
     * <li>Category 76 (Block Libraries) → {@link AddonType#AUTOMATION}</li>
     * <li>Category 80 (Transformations) → {@link AddonType#TRANSFORMATION}</li>
     * </ul>
     *
     * <p>
     * For the Bundles category, the method looks for tags matching standard openHAB addon type IDs
     * (e.g., "binding", "persistence", "transformation").
     *
     * @param category the Discourse category ID
     * @param tags the list of tags attached to the topic
     * @return the corresponding addon type, or null if type cannot be determined
     */
    private @Nullable AddonType getAddonType(@Nullable Integer category, List<String> tags) {
        // check if we can determine the addon type from the category
        if (TRANSFORMATIONS_CATEGORY.equals(category)) {
            return AddonType.TRANSFORMATION;
        } else if (RULETEMPLATES_CATEGORY.equals(category)) {
            return AddonType.AUTOMATION;
        } else if (UIWIDGETS_CATEGORY.equals(category)) {
            return AddonType.UI;
        } else if (BLOCKLIBRARIES_CATEGORY.equals(category)) {
            return AddonType.AUTOMATION;
        } else if (BUNDLES_CATEGORY.equals(category)) {
            // try to get it from tags if we have tags
            return AddonType.DEFAULT_TYPES.stream().filter(type -> tags.contains(type.getId())).findFirst()
                    .orElse(null);
        }

        // or return null
        return null;
    }

    /**
     * Determines the marketplace content type from Discourse category and tags.
     *
     * <p>
     * Content types indicate the installation mechanism required (JAR/KAR file vs. JSON/YAML config).
     *
     * <p>
     * <b>Category mapping:</b>
     * <ul>
     * <li>Category 73 (Bundles) → {@code JAR_CONTENT_TYPE} or {@code KAR_CONTENT_TYPE} (if "kar" tag present)</li>
     * <li>Category 74 (Rule Templates) → {@code RULETEMPLATES_CONTENT_TYPE}</li>
     * <li>Category 75 (UI Widgets) → {@code UIWIDGETS_CONTENT_TYPE}</li>
     * <li>Category 76 (Block Libraries) → {@code BLOCKLIBRARIES_CONTENT_TYPE}</li>
     * <li>Category 80 (Transformations) → {@code TRANSFORMATIONS_CONTENT_TYPE}</li>
     * </ul>
     *
     * @param category the Discourse category ID
     * @param tags the list of tags attached to the topic
     * @return the content type identifier, or empty string if type cannot be determined
     */
    private String getContentType(@Nullable Integer category, List<String> tags) {
        // check if we can determine the addon type from the category
        if (TRANSFORMATIONS_CATEGORY.equals(category)) {
            return TRANSFORMATIONS_CONTENT_TYPE;
        } else if (RULETEMPLATES_CATEGORY.equals(category)) {
            return RULETEMPLATES_CONTENT_TYPE;
        } else if (UIWIDGETS_CATEGORY.equals(category)) {
            return UIWIDGETS_CONTENT_TYPE;
        } else if (BLOCKLIBRARIES_CATEGORY.equals(category)) {
            return BLOCKLIBRARIES_CONTENT_TYPE;
        } else if (BUNDLES_CATEGORY.equals(category)) {
            if (tags.contains("kar")) {
                return KAR_CONTENT_TYPE;
            } else {
                // default to plain jar bundle for addons
                return JAR_CONTENT_TYPE;
            }
        }

        // empty string if content type could not be defined
        return "";
    }

    /**
     * Converts a lightweight Discourse topic listing into an {@link Addon} object.
     *
     * <p>
     * This method is used when fetching the marketplace addon list (not full details).
     * It extracts metadata from the topic title, tags, and category to construct an addon.
     *
     * <p>
     * <b>Version compatibility parsing:</b> If the title ends with a version range in brackets
     * (e.g., "My Addon [3.0.0,4.0.0)"), the method:
     * <ol>
     * <li>Extracts and validates the range</li>
     * <li>Checks if current openHAB version falls within the range</li>
     * <li>Strips the range from the displayed title</li>
     * </ol>
     *
     * <p>
     * <b>Installation status:</b> Queries all registered handlers to determine if the addon
     * is currently installed.
     *
     * @param topic the lightweight topic item from Discourse API list response
     * @param users the list of users from the same API response (for author lookup)
     * @return the addon object, or null if type cannot be determined or parsing fails
     */
    private @Nullable Addon convertTopicItemToAddon(DiscourseTopicItem topic, List<DiscourseUser> users) {
        try {
            List<String> tags = Arrays.asList(Objects.requireNonNullElse(topic.tags, new String[0]));

            String uid = ADDON_ID_PREFIX + topic.id.toString();
            AddonType addonType = getAddonType(topic.categoryId, tags);
            if (addonType == null) {
                logger.debug("Ignoring topic '{}' because no add-on type could be found", topic.id);
                return null;
            }
            String type = addonType.getId();
            String id = topic.id.toString(); // this will be replaced after installation by the correct id if available
            String contentType = getContentType(topic.categoryId, tags);

            String title = topic.title;
            boolean compatible = true;

            int compatibilityStart = topic.title.lastIndexOf("["); // version range always starts with [
            if (topic.title.lastIndexOf(" ") < compatibilityStart) { // check includes [ not present
                String potentialRange = topic.title.substring(compatibilityStart);
                Matcher matcher = BundleVersion.RANGE_PATTERN.matcher(potentialRange);
                if (matcher.matches()) {
                    try {
                        compatible = coreVersion.inRange(potentialRange);
                        title = topic.title.substring(0, compatibilityStart).trim();
                        logger.debug("{} is {}compatible with core version {}", topic.title, compatible ? "" : "NOT ",
                                coreVersion);
                    } catch (IllegalArgumentException e) {
                        logger.debug("Failed to determine compatibility for addon {}: {}", topic.title, e.getMessage());
                        compatible = true;
                    }
                } else {
                    logger.debug("Range pattern does not match '{}'", potentialRange);
                }
            }

            String link = COMMUNITY_TOPIC_URL + topic.id.toString();
            int likeCount = topic.likeCount;
            int views = topic.views;
            int postsCount = topic.postsCount;
            Date createdDate = topic.createdAt;
            String author = "";
            for (DiscoursePosterInfo posterInfo : topic.posters) {
                if (posterInfo.description.contains("Original Poster")) {
                    author = users.stream().filter(u -> u.id.equals(posterInfo.userId)).findFirst().get().name;
                }
            }

            String maturity = tags.stream().filter(CODE_MATURITY_LEVELS::contains).findAny().orElse(null);

            Map<String, Object> properties = Map.of("created_at", createdDate, //
                    "like_count", likeCount, //
                    "views", views, //
                    "posts_count", postsCount, //
                    "tags", tags.toArray(String[]::new));

            // try to use a handler to determine if the add-on is installed
            boolean installed = addonHandlers.stream()
                    .anyMatch(handler -> handler.supports(type, contentType) && handler.isInstalled(uid));

            return Addon.create(uid).withType(type).withId(id).withContentType(contentType)
                    .withImageLink(topic.imageUrl).withAuthor(author).withProperties(properties).withLabel(title)
                    .withInstalled(installed).withMaturity(maturity).withCompatible(compatible).withLink(link).build();
        } catch (RuntimeException e) {
            logger.debug("Ignoring marketplace add-on '{}' due: {}", topic.title, e.getMessage());
            return null;
        }
    }

    /**
     * Unescapes occurrences of XML entities found in the supplied content.
     *
     * @param content the content with potentially escaped entities
     * @return the unescaped content
     */
    private String unescapeEntities(String content) {
        return content.replace("&quot;", "\"").replace("&amp;", "&").replace("&apos;", "'").replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    /**
     * Converts a full Discourse topic response into an {@link Addon} object with complete details.
     *
     * <p>
     * This method is used when fetching a single addon's full details (not the list view).
     * It extracts extensive metadata including:
     * <ul>
     * <li>Download URLs for JAR/KAR/JSON/YAML files (from post links)</li>
     * <li>Inline code content (JSON/YAML embedded in posts)</li>
     * <li>Detailed description (full post HTML)</li>
     * <li>Statistics (views, likes, posts)</li>
     * </ul>
     *
     * <p>
     * <b>ID determination:</b> The method attempts to extract a meaningful addon ID from
     * download URLs (e.g., "my-binding" from "my-binding-1.0.0.jar"). Falls back to topic ID
     * if no URLs are found.
     *
     * <p>
     * <b>HTML entity handling:</b> Code content extracted from posts is unescaped
     * (e.g., {@code &quot;} → {@code "}).
     *
     * @param topic the full topic response from Discourse API
     * @return the addon object with complete details
     */
    private Addon convertTopicToAddon(DiscourseTopicResponseDTO topic) {
        String uid = ADDON_ID_PREFIX + topic.id.toString();
        List<String> tags = Arrays.asList(Objects.requireNonNullElse(topic.tags, new String[0]));

        AddonType addonType = getAddonType(topic.categoryId, tags);
        String type = (addonType != null) ? addonType.getId() : "";
        String contentType = getContentType(topic.categoryId, tags);

        int likeCount = topic.likeCount;
        int views = topic.views;
        int postsCount = topic.postsCount;
        Date createdDate = topic.postStream.posts[0].createdAt;
        Date updatedDate = topic.postStream.posts[0].updatedAt;
        Date lastPostedDate = topic.lastPosted;

        String maturity = tags.stream().filter(CODE_MATURITY_LEVELS::contains).findAny().orElse(null);

        Map<String, Object> properties = new HashMap<>(10);
        properties.put("created_at", createdDate);
        properties.put("updated_at", updatedDate);
        properties.put("last_posted", lastPostedDate);
        properties.put("like_count", likeCount);
        properties.put("views", views);
        properties.put("posts_count", postsCount);
        properties.put("tags", tags.toArray(String[]::new));

        String detailedDescription = topic.postStream.posts[0].cooked;
        String id = null;

        // try to extract contents or links
        if (topic.postStream.posts[0].linkCounts != null) {
            for (DiscoursePostLink postLink : topic.postStream.posts[0].linkCounts) {
                if (postLink.url.endsWith(".jar")) {
                    properties.put(JAR_DOWNLOAD_URL_PROPERTY, postLink.url);
                    id = determineIdFromUrl(postLink.url);
                }
                if (postLink.url.endsWith(".kar")) {
                    properties.put(KAR_DOWNLOAD_URL_PROPERTY, postLink.url);
                    id = determineIdFromUrl(postLink.url);
                }
                if (postLink.url.endsWith(".json")) {
                    properties.put(JSON_DOWNLOAD_URL_PROPERTY, postLink.url);
                }
                if (postLink.url.endsWith(".yaml")) {
                    properties.put(YAML_DOWNLOAD_URL_PROPERTY, postLink.url);
                }
            }
        }

        if (id == null) {
            id = topic.id.toString(); // this is a fallback if we couldn't find a better id
        }

        Matcher codeMarkup = CODE_MARKUP_PATTERN.matcher(detailedDescription);
        if (codeMarkup.find()) {
            properties.put(codeMarkup.group("lang") + CODE_CONTENT_SUFFIX,
                    unescapeEntities(codeMarkup.group("content")));
        }

        // try to use a handler to determine if the add-on is installed
        boolean installed = addonHandlers.stream()
                .anyMatch(handler -> handler.supports(type, contentType) && handler.isInstalled(uid));

        String title = topic.title;
        int compatibilityStart = topic.title.lastIndexOf("["); // version range always starts with [
        if (topic.title.lastIndexOf(" ") < compatibilityStart) { // check includes [ not present
            String potentialRange = topic.title.substring(compatibilityStart);
            Matcher matcher = BundleVersion.RANGE_PATTERN.matcher(potentialRange);
            if (matcher.matches()) {
                title = topic.title.substring(0, compatibilityStart).trim();
            }
        }

        Addon.Builder builder = Addon.create(uid).withType(type).withId(id).withContentType(contentType)
                .withLabel(title).withImageLink(topic.imageUrl).withLink(COMMUNITY_TOPIC_URL + topic.id.toString())
                .withAuthor(topic.postStream.posts[0].displayUsername).withMaturity(maturity)
                .withDetailedDescription(detailedDescription).withInstalled(installed).withProperties(properties);

        return builder.build();
    }

    /**
     * Extracts a meaningful addon ID from a download URL.
     *
     * <p>
     * Attempts to parse the bundle name from URLs like:
     * <ul>
     * <li>{@code https://example.com/org.openhab.binding.mybinding-1.0.0.jar} → {@code mybinding}</li>
     * <li>{@code /path/to/my-addon-bundle-2.5.0.jar} → {@code bundle}</li>
     * </ul>
     *
     * <p>
     * <b>Parsing strategy:</b> Uses regex pattern to match files ending in
     * {@code <name>-<major>.<minor>.<patch>.<extension>}. Extracts the last segment after the
     * final dot in the name portion.
     *
     * <p>
     * <b>Limitations:</b> The current pattern assumes semantic versioning with exactly three parts.
     * Version formats like {@code -SNAPSHOT}, {@code .RELEASE}, {@code -beta1} may not parse correctly.
     *
     * @param url the download URL for a JAR or KAR file
     * @return the extracted addon ID, or null if URL doesn't match expected pattern
     */
    private @Nullable String determineIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            logger.debug("Cannot determine addon ID from null or empty URL");
            return null;
        }

        Matcher matcher = BUNDLE_NAME_PATTERN.matcher(url);
        if (matcher.matches() && matcher.groupCount() >= 1) {
            String bundleName = matcher.group(1);
            if (bundleName != null && !bundleName.isEmpty()) {
                int lastDotIndex = bundleName.lastIndexOf(".");
                if (lastDotIndex >= 0 && lastDotIndex < bundleName.length() - 1) {
                    return bundleName.substring(lastDotIndex + 1);
                }
                // No dots in bundle name - return as-is (e.g., "mybinding-1.0.0.jar" → "mybinding")
                return bundleName;
            }
        }

        logger.debug("Could not determine bundle name from URL '{}'. Expected format: '<name>-<version>.jar'", url);
        return null;
    }
}

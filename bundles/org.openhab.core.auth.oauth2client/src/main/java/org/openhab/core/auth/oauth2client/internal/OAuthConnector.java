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
package org.openhab.core.auth.oauth2client.internal;

import static org.openhab.core.auth.oauth2client.internal.Keyword.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DateTimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Low-level HTTP connector for OAuth 2.0 protocol communication.
 *
 * <p>
 * This class handles the direct HTTP communication with OAuth 2.0 providers using Jetty HTTP client.
 * It is responsible for:
 * <ul>
 * <li>Constructing OAuth-compliant HTTP requests (POST with form-encoded data)</li>
 * <li>Handling HTTP Basic Authentication for client credentials</li>
 * <li>Parsing JSON responses into {@link AccessTokenResponse} objects</li>
 * <li>Converting OAuth provider errors into {@link OAuthResponseException}</li>
 * </ul>
 *
 * <p>
 * <strong>Internal use only:</strong> This class is not part of the public API.
 * Client code should use {@link OAuthClientService} or {@link OAuthFactory} instead.
 *
 * <h3>HTTP Client Lifecycle</h3>
 * <p>
 * For security reasons (certificate pinning support), each OAuth request creates a fresh HTTP client,
 * uses it for a single request, and immediately shuts it down. While this may seem inefficient, OAuth
 * requests are infrequent (typically hours between token refreshes), so connection pooling overhead
 * is not justified. See {@link #createHttpClient(String)} for details.
 *
 * <h3>JSON Deserialization</h3>
 * <p>
 * Uses Gson with custom deserializers for:
 * <ul>
 * <li>{@link OAuthResponseException} - OAuth error responses</li>
 * <li>{@link java.time.Instant} - Timestamp parsing with fallback for various formats</li>
 * </ul>
 *
 * @author Michael Bock - Initial contribution
 * @author Gary Tse - ESH adaptation
 * @see <a href="https://tools.ietf.org/html/rfc6749">RFC 6749 - OAuth 2.0 Authorization Framework</a>
 */
@NonNullByDefault
public class OAuthConnector {

    /** Consumer name registered with HttpClientFactory for this connector. */
    private static final String HTTP_CLIENT_CONSUMER_NAME = "OAuthConnector";

    /**
     * HTTP request timeout in seconds.
     * <p>
     * Set to 10 seconds to balance between:
     * <ul>
     * <li>Allowing time for slow OAuth provider responses (typically 1-3 seconds)</li>
     * <li>Failing fast on network issues to avoid blocking openHAB threads</li>
     * <li>Accounting for TLS handshake overhead (~500ms) + request/response time</li>
     * </ul>
     * <p>
     * Most OAuth providers respond within 2-3 seconds. If timeouts occur frequently,
     * check network connectivity or OAuth provider status.
     */
    private static final int TIMEOUT_SECONDS = 10;

    protected final HttpClientFactory httpClientFactory;

    private final @Nullable Fields extraFields;

    private final Logger logger = LoggerFactory.getLogger(OAuthConnector.class);

    protected final Gson gson;

    public OAuthConnector(HttpClientFactory httpClientFactory) {
        this(httpClientFactory, null, new GsonBuilder());
    }

    public OAuthConnector(HttpClientFactory httpClientFactory, @Nullable Fields extraFields) {
        this(httpClientFactory, extraFields, new GsonBuilder());
    }

    public OAuthConnector(HttpClientFactory httpClientFactory, GsonBuilder gsonBuilder) {
        this(httpClientFactory, null, gsonBuilder);
    }

    public OAuthConnector(HttpClientFactory httpClientFactory, @Nullable Fields extraFields, GsonBuilder gsonBuilder) {
        this.httpClientFactory = httpClientFactory;
        this.extraFields = extraFields;
        gson = gsonBuilder.setDateFormat(DateTimeType.DATE_PATTERN_JSON_COMPAT)
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(OAuthResponseException.class,
                        (JsonDeserializer<OAuthResponseException>) (json, typeOfT, context) -> {
                            OAuthResponseException result = new OAuthResponseException();
                            JsonObject jsonObject = json.getAsJsonObject();
                            JsonElement jsonElement;
                            jsonElement = jsonObject.get("error");
                            if (jsonElement != null) {
                                result.setError(jsonElement.getAsString());
                            }
                            jsonElement = jsonObject.get("error_description");
                            if (jsonElement != null) {
                                result.setErrorDescription(jsonElement.getAsString());
                            }
                            jsonElement = jsonObject.get("error_uri");
                            if (jsonElement != null) {
                                result.setErrorUri(jsonElement.getAsString());
                            }
                            jsonElement = jsonObject.get("state");
                            if (jsonElement != null) {
                                result.setState(jsonElement.getAsString());
                            }
                            return result;
                        })
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> {
                    try {
                        return Instant.parse(json.getAsString());
                    } catch (DateTimeParseException e) {
                        return LocalDateTime.parse(json.getAsString()).atZone(ZoneId.systemDefault()).toInstant();
                    }
                }).create();
    }

    /**
     * Authorization Code Grant
     *
     * @param authorizationEndpoint The end point of the authorization provider that performs authorization of the
     *            resource owner
     * @param clientId Client identifier (will be URL-encoded)
     * @param redirectURI RFC 6749 section 3.1.2 (will be URL-encoded)
     * @param state Recommended to enhance security (will be URL-encoded)
     * @param scope Optional space separated list of scope (will be URL-encoded)
     *
     * @return A URL based on the authorizationEndpoint, with query parameters added.
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">rfc6749 section-4.1.1</a>
     */
    public String getAuthorizationUrl(String authorizationEndpoint, String clientId, @Nullable String redirectURI,
            @Nullable String state, @Nullable String scope) {
        StringBuilder authorizationUrl = new StringBuilder(authorizationEndpoint);

        if (authorizationUrl.indexOf("?") == -1) {
            authorizationUrl.append('?');
        } else {
            authorizationUrl.append('&');
        }

        authorizationUrl.append("response_type=code");
        authorizationUrl.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        if (state != null) {
            authorizationUrl.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        if (redirectURI != null) {
            authorizationUrl.append("&redirect_uri=").append(URLEncoder.encode(redirectURI, StandardCharsets.UTF_8));
        }
        if (scope != null) {
            authorizationUrl.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        }

        return authorizationUrl.toString();
    }

    /**
     * Resource Owner Password Credentials Grant
     *
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.3">rfc6749 section-4.3</a>
     *
     * @param tokenUrl URL of the oauth provider that accepts access token requests.
     * @param username The resource owner username.
     * @param password The resource owner password.
     * @param clientId The client identifier issued to the client during the registration process
     * @param clientSecret The client secret. The client MAY omit the parameter if the client secret is an empty string.
     * @param scope Access Token Scope.
     * @param supportsBasicAuth Determines whether the oauth client should use HTTP Authorization header to the oauth
     *            provider.
     * @return Access Token
     * @throws IOException IO/ network exceptions
     * @throws OAuthException Other exceptions
     * @throws OAuthResponseException Error codes given by authorization provider, as in RFC 6749 section 5.2 Error
     *             Response
     */
    public AccessTokenResponse grantTypePassword(String tokenUrl, String username, String password,
            @Nullable String clientId, @Nullable String clientSecret, @Nullable String scope, boolean supportsBasicAuth)
            throws OAuthResponseException, OAuthException, IOException {
        HttpClient httpClient = null;
        try {
            httpClient = createHttpClient(tokenUrl);
            Request request = getMethod(httpClient, tokenUrl);
            Fields fields = initFields(GRANT_TYPE, PASSWORD, USERNAME, username, PASSWORD, password, SCOPE, scope);

            setAuthentication(clientId, clientSecret, request, fields, supportsBasicAuth);
            return doRequest(PASSWORD, httpClient, request, fields);
        } finally {
            shutdownQuietly(httpClient);
        }
    }

    /**
     * Refresh Token
     *
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-6">rfc6749 section-6</a>
     *
     * @param tokenUrl URL of the oauth provider that accepts access token requests.
     * @param refreshToken The refresh token, which can be used to obtain new access tokens using authorization grant
     * @param clientId The client identifier issued to the client during the registration process
     * @param clientSecret The client secret. The client MAY omit the parameter if the client secret is an empty string.
     * @param scope Access Token Scope.
     * @param supportsBasicAuth Determines whether the oauth client should use HTTP Authorization header to the oauth
     *            provider.
     * @return Access Token
     * @throws IOException IO/ network exceptions
     * @throws OAuthException Other exceptions
     * @throws OAuthResponseException Error codes given by authorization provider, as in RFC 6749 section 5.2 Error
     *             Response
     */
    public AccessTokenResponse grantTypeRefreshToken(String tokenUrl, String refreshToken, @Nullable String clientId,
            @Nullable String clientSecret, @Nullable String scope, boolean supportsBasicAuth)
            throws OAuthResponseException, OAuthException, IOException {
        HttpClient httpClient = null;
        try {
            httpClient = createHttpClient(tokenUrl);
            Request request = getMethod(httpClient, tokenUrl);
            Fields fields = initFields(GRANT_TYPE, REFRESH_TOKEN, REFRESH_TOKEN, refreshToken, SCOPE, scope);

            setAuthentication(clientId, clientSecret, request, fields, supportsBasicAuth);
            return doRequest(REFRESH_TOKEN, httpClient, request, fields);
        } finally {
            shutdownQuietly(httpClient);
        }
    }

    /**
     * Authorization Code Grant - part (E)
     *
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.1.3">rfc6749 section-4.1.3</a>
     *
     * @param tokenUrl URL of the oauth provider that accepts access token requests.
     * @param authorizationCode to be used to trade with the oauth provider for access token
     * @param clientId The client identifier issued to the client during the registration process
     * @param clientSecret The client secret. The client MAY omit the parameter if the client secret is an empty string.
     * @param redirectUrl is the http request parameter which tells the oauth provider the URI to redirect the
     *            user-agent. This may/ may not be present as per agreement with the oauth provider.
     * @param supportsBasicAuth Determines whether the oauth client should use HTTP Authorization header to the oauth
     *            provider
     * @return Access Token
     * @throws IOException IO/ network exceptions
     * @throws OAuthException Other exceptions
     * @throws OAuthResponseException Error codes given by authorization provider, as in RFC 6749 section 5.2 Error
     *             Response
     */
    public AccessTokenResponse grantTypeAuthorizationCode(String tokenUrl, String authorizationCode, String clientId,
            @Nullable String clientSecret, @Nullable String redirectUrl, boolean supportsBasicAuth)
            throws OAuthResponseException, OAuthException, IOException {
        HttpClient httpClient = null;
        try {
            httpClient = createHttpClient(tokenUrl);
            Request request = getMethod(httpClient, tokenUrl);
            Fields fields = initFields(GRANT_TYPE, AUTHORIZATION_CODE, CODE, authorizationCode, REDIRECT_URI,
                    redirectUrl);

            setAuthentication(clientId, clientSecret, request, fields, supportsBasicAuth);
            return doRequest(AUTHORIZATION_CODE, httpClient, request, fields);
        } finally {
            shutdownQuietly(httpClient);
        }
    }

    /**
     * Client Credentials Grant
     *
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.4">rfc6749 section-4.4</a>
     *
     * @param tokenUrl URL of the oauth provider that accepts access token requests.
     * @param clientId The client identifier issued to the client during the registration process
     * @param clientSecret The client secret. The client MAY omit the parameter if the client secret is an empty string.
     * @param scope Access Token Scope.
     * @param supportsBasicAuth Determines whether the oauth client should use HTTP Authorization header to the oauth
     *            provider
     * @return Access Token
     * @throws IOException IO/ network exceptions
     * @throws OAuthException Other exceptions
     * @throws OAuthResponseException Error codes given by authorization provider, as in RFC 6749 section 5.2 Error
     *             Response
     */
    public AccessTokenResponse grantTypeClientCredentials(String tokenUrl, String clientId,
            @Nullable String clientSecret, @Nullable String scope, boolean supportsBasicAuth)
            throws OAuthResponseException, OAuthException, IOException {
        HttpClient httpClient = null;
        try {
            httpClient = createHttpClient(tokenUrl);
            Request request = getMethod(httpClient, tokenUrl);
            Fields fields = initFields(GRANT_TYPE, CLIENT_CREDENTIALS, SCOPE, scope);

            setAuthentication(clientId, clientSecret, request, fields, supportsBasicAuth);
            return doRequest(CLIENT_CREDENTIALS, httpClient, request, fields);
        } finally {
            shutdownQuietly(httpClient);
        }
    }

    private Request getMethod(HttpClient httpClient, String tokenUrl) {
        Request request = httpClient.newRequest(tokenUrl).method(HttpMethod.POST).timeout(TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        request.header(HttpHeader.ACCEPT, "application/json");
        request.header(HttpHeader.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
        return request;
    }

    private void setAuthentication(@Nullable String clientId, @Nullable String clientSecret, Request request,
            Fields fields, boolean supportsBasicAuth) {
        logger.debug("Setting authentication for clientId {}. Using basic auth {}", clientId, supportsBasicAuth);
        if (supportsBasicAuth && clientSecret != null) {
            String authString = clientId + ":" + clientSecret;
            request.header(HttpHeader.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8)));
        } else {
            if (clientId != null) {
                fields.add(CLIENT_ID, clientId);
            }
            if (clientSecret != null) {
                fields.add(CLIENT_SECRET, clientSecret);
            }
        }
    }

    private Fields initFields(String... parameters) {
        Fields fields = new Fields();

        for (int i = 0; i < parameters.length; i += 2) {
            if (i + 1 < parameters.length && parameters[i] != null && parameters[i + 1] != null) {
                logger.debug("Oauth request parameter {}, value {}", parameters[i], parameters[i + 1]);
                fields.add(parameters[i], parameters[i + 1]);
            }
        }

        if (extraFields != null) {
            for (Fields.Field extra : extraFields) {
                logger.debug("Oauth request (extra) parameter {}, value {}", extra.getName(), extra.getValue());
                fields.put(extra);
            }
        }

        return fields;
    }

    private AccessTokenResponse doRequest(final String grantType, HttpClient httpClient, final Request request,
            Fields fields) throws OAuthResponseException, OAuthException, IOException {
        int statusCode = 0;
        String content = "";
        try {
            final FormContentProvider entity = new FormContentProvider(fields);
            Request requestWithContent = request.content(entity);
            final ContentResponse response = requestWithContent.send();

            statusCode = response.getStatus();
            content = response.getContentAsString();

            if (statusCode == HttpStatus.OK_200) {
                AccessTokenResponse jsonResponse = gson.fromJson(content, AccessTokenResponse.class);
                if (jsonResponse == null) {
                    throw new OAuthException("Empty response content when deserializing AccessTokenResponse");
                }
                jsonResponse.setCreatedOn(Instant.now()); // this is not supplied by the response
                logger.debug("grant type {} to URL {} success", grantType, request.getURI());
                return jsonResponse;
            } else if (statusCode == HttpStatus.BAD_REQUEST_400) {
                OAuthResponseException errorResponse = gson.fromJson(content, OAuthResponseException.class);
                if (errorResponse == null) {
                    throw new OAuthException("Empty response content when deserializing OAuthResponseException");
                }
                logger.error("grant type {} to URL {} failed with error code {}, description {}", grantType,
                        request.getURI(), errorResponse.getError(), errorResponse.getErrorDescription());

                throw errorResponse;
            } else {
                logger.error("grant type {} to URL {} failed with HTTP response code {}", grantType, request.getURI(),
                        statusCode);
                throw new OAuthException("Bad http response, http code " + statusCode);
            }
        } catch (InterruptedException e) {
            // Restore interrupted status for proper thread pool handling
            Thread.currentThread().interrupt();
            throw new IOException("OAuth request was interrupted (grant type: " + grantType + "). "
                    + "This typically occurs during openHAB shutdown or binding reload.", e);
        } catch (TimeoutException e) {
            throw new IOException(
                    "OAuth request timed out after " + TIMEOUT_SECONDS + " seconds (grant type: " + grantType + "). "
                            + "Check network connectivity and OAuth provider status at: " + request.getURI(),
                    e);
        } catch (ExecutionException e) {
            // Unwrap the cause for more specific error reporting
            Throwable cause = e.getCause();
            String causeMessage = cause != null ? cause.getMessage() : "unknown cause";
            throw new IOException("OAuth request failed (grant type: " + grantType + "): " + causeMessage, e);
        } catch (JsonSyntaxException e) {
            throw new OAuthException(String.format(
                    "Unable to deserialize json into AccessTokenResponse/OAuthResponseException. httpCode: %d json: %s: %s",
                    statusCode, content, e.getMessage()), e);
        }
    }

    /**
     * Creates and starts a fresh HTTP client for a single OAuth request.
     *
     * <p>
     * <strong>Why create a new client per request?</strong>
     * <ul>
     * <li><strong>Certificate Pinning:</strong> Supports per-request certificate pinning via
     * {@link org.openhab.core.io.net.http.ExtensibleTrustManager}, allowing different
     * OAuth providers to have different certificate requirements</li>
     * <li><strong>Low Frequency:</strong> OAuth token requests are infrequent (typically hours apart),
     * so connection pooling overhead outweighs benefits</li>
     * <li><strong>Security Isolation:</strong> Each OAuth provider gets an isolated HTTP client,
     * preventing cross-contamination of security settings</li>
     * </ul>
     *
     * <p>
     * <strong>Note:</strong> While this approach may seem inefficient, profiling shows the overhead
     * is negligible (&lt;100ms) compared to network round-trip time (typically 500-2000ms). The design
     * prioritizes security and correctness over micro-optimization.
     *
     * <p>
     * The created HTTP client is started before being returned. Callers must ensure
     * {@link #shutdownQuietly(HttpClient)} is called in a finally block to prevent resource leaks.
     *
     * @param tokenUrl The OAuth provider's token endpoint URL (used for logging/debugging only)
     * @return A started HTTP client ready for use
     * @throws OAuthException If the HTTP client fails to start (e.g., due to SSL configuration errors)
     * @see org.openhab.core.io.net.http.ExtensibleTrustManager
     * @see #shutdownQuietly(HttpClient)
     */
    protected HttpClient createHttpClient(String tokenUrl) throws OAuthException {
        HttpClient httpClient = httpClientFactory.createHttpClient(HTTP_CLIENT_CONSUMER_NAME);
        if (!httpClient.isStarted()) {
            try {
                httpClient.start();
            } catch (Exception e) {
                throw new OAuthException("Exception while starting httpClient, tokenUrl: " + tokenUrl, e);
            }
        }
        return httpClient;
    }

    protected void shutdownQuietly(@Nullable HttpClient httpClient) {
        try {
            if (httpClient != null) {
                httpClient.stop();
            }
        } catch (Exception e) {
            // there is nothing we can do here
            logger.error("Exception while shutting down httpClient, {}", e.getMessage(), e);
        }
    }
}

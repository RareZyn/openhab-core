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
package org.openhab.core.auth.jaas.internal;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.Authentication;
import org.openhab.core.auth.AuthenticationException;
import org.openhab.core.auth.AuthenticationProvider;
import org.openhab.core.auth.Credentials;
import org.openhab.core.auth.GenericUser;
import org.openhab.core.auth.UsernamePasswordCredentials;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

/**
 * Implementation of authentication provider which is backed by JAAS realm.
 *
 * Real authentication logic is embedded in login modules implemented by 3rd party, this code is just for bridging it.
 *
 * @author Łukasz Dywicki - Initial contribution
 * @author Kai Kreuzer - Removed ManagedService and used DS configuration instead
 * @author Yannick Schaus - provides a configuration with the ManagedUserLoginModule as a sufficient login module
 */
@NonNullByDefault
@Component(configurationPid = "org.openhab.jaas")
public class JaasAuthenticationProvider implements AuthenticationProvider {
    private static final String DEFAULT_REALM = "openhab";

    private @Nullable String realmName;

    /**
     * Authenticates the provided credentials using JAAS.
     *
     * This method performs the following steps:
     * 1. Validates the credentials type and content
     * 2. Switches the context ClassLoader to enable JAAS to load the login module
     * 3. Creates a JAAS LoginContext and performs authentication
     * 4. Extracts roles from the authenticated subject
     * 5. Clears sensitive password data from memory
     *
     * Note: ClassLoader switching is required because JAAS uses the context ClassLoader
     * to discover login modules, and in an OSGi environment, the calling bundle's
     * ClassLoader may not have visibility to the authentication infrastructure.
     *
     * @param credentials the credentials to authenticate (must be UsernamePasswordCredentials)
     * @return an Authentication object containing the username and roles
     * @throws AuthenticationException if authentication fails or credentials are invalid
     */
    @Override
    public Authentication authenticate(final Credentials credentials) throws AuthenticationException {
        if (realmName == null) { // configuration is not yet ready or set
            realmName = DEFAULT_REALM;
        }

        if (!(credentials instanceof UsernamePasswordCredentials)) {
            throw new AuthenticationException("Unsupported credentials passed to provider.");
        }

        UsernamePasswordCredentials userCredentials = (UsernamePasswordCredentials) credentials;
        final String name = userCredentials.getUsername();
        final String passwordString = userCredentials.getPassword();

        // Validate username
        if (name.isBlank()) {
            throw new AuthenticationException("Username cannot be empty.");
        }

        // Validate password
        if (passwordString.isEmpty()) {
            throw new AuthenticationException("Password cannot be empty.");
        }

        final char[] password = passwordString.toCharArray();
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Principal userPrincipal = new GenericUser(name);
            Subject subject = new Subject(true, Set.of(userPrincipal), Set.of(), Set.of(userCredentials));

            Thread.currentThread().setContextClassLoader(ManagedUserLoginModule.class.getClassLoader());
            LoginContext loginContext = new LoginContext(realmName, subject, new CallbackHandler() {
                @Override
                public void handle(@NonNullByDefault({}) Callback[] callbacks)
                        throws IOException, UnsupportedCallbackException {
                    for (Callback callback : callbacks) {
                        if (callback instanceof PasswordCallback passwordCallback) {
                            passwordCallback.setPassword(password);
                        } else if (callback instanceof NameCallback nameCallback) {
                            nameCallback.setName(name);
                        } else {
                            throw new UnsupportedCallbackException(callback);
                        }
                    }
                }
            }, new ManagedUserLoginConfiguration());
            loginContext.login();

            return getAuthentication(name, loginContext.getSubject());
        } catch (LoginException e) {
            String message = e.getMessage();
            throw new AuthenticationException(message != null ? message : "An unexpected LoginException occurred");
        } finally {
            // Security: Clear password from memory to reduce exposure window
            Arrays.fill(password, '\0');
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    /**
     * Creates an Authentication object from the authenticated subject.
     *
     * @param name the username
     * @param subject the authenticated JAAS subject containing principals (roles)
     * @return an Authentication object with the username and extracted roles
     */
    private Authentication getAuthentication(String name, Subject subject) {
        return new Authentication(name, getRoles(subject.getPrincipals()));
    }

    /**
     * Extracts role names from the set of principals.
     *
     * All principals in the subject are converted to role strings. This includes
     * both the user principal and any additional role principals added by the
     * login module.
     *
     * @param principals the set of principals from the authenticated subject
     * @return an array of role names
     */
    private String[] getRoles(Set<Principal> principals) {
        return principals.stream().map(Principal::getName).collect(Collectors.toList()).toArray(new String[0]);
    }

    @Activate
    protected void activate(Map<String, Object> properties) {
        modified(properties);
    }

    /**
     * Called when the component is deactivated.
     *
     * No cleanup is required because:
     * - The JAAS LoginContext is created per-authentication and automatically cleaned up
     * - No persistent state or resources are held by this provider
     * - The realm configuration is lightweight and doesn't require disposal
     *
     * @param properties the component properties (unused)
     */
    @Deactivate
    protected void deactivate(Map<String, Object> properties) {
        // No cleanup required - stateless authentication provider
    }

    @Modified
    protected void modified(@Nullable Map<String, Object> properties) {
        if (properties == null) {
            realmName = DEFAULT_REALM;
            return;
        }

        Object propertyValue = properties.get("realmName");
        if (propertyValue != null) {
            if (propertyValue instanceof String string) {
                realmName = string;
            } else {
                realmName = propertyValue.toString();
            }
        } else {
            // value could be unset, we should reset its value
            realmName = DEFAULT_REALM;
        }
    }

    @Override
    public boolean supports(Class<? extends Credentials> type) {
        return UsernamePasswordCredentials.class.isAssignableFrom(type);
    }
}

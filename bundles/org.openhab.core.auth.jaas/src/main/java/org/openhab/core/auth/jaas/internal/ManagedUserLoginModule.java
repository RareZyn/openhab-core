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

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.AuthenticationException;
import org.openhab.core.auth.Credentials;
import org.openhab.core.auth.UserRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link LoginModule} delegates the authentication to a {@link UserRegistry}.
 *
 * This login module is part of openHAB's JAAS-based authentication system.
 * It retrieves the UserRegistry service from the OSGi service registry and
 * uses it to authenticate the credentials provided in the Subject.
 *
 * <p>
 * <b>Thread-safety:</b> Each authentication attempt creates a new instance of this
 * login module, so no synchronization is required for instance variables.
 * </p>
 *
 * @author Yannick Schaus - initial contribution
 */
public class ManagedUserLoginModule implements LoginModule {

    private final Logger logger = LoggerFactory.getLogger(ManagedUserLoginModule.class);

    private @Nullable UserRegistry userRegistry;
    private @Nullable ServiceReference<UserRegistry> serviceReference;
    private @Nullable BundleContext bundleContext;

    private Subject subject;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
            Map<String, ?> options) {
        this.subject = subject;
    }

    /**
     * Performs the authentication by retrieving the UserRegistry service and
     * delegating the credential verification to it.
     *
     * @return true if authentication succeeds
     * @throws LoginException if authentication fails or if the UserRegistry service cannot be obtained
     */
    @Override
    public boolean login() throws LoginException {
        try {
            // try to get the UserRegistry instance
            bundleContext = FrameworkUtil.getBundle(UserRegistry.class).getBundleContext();
            if (bundleContext == null) {
                logger.error("Cannot obtain BundleContext for UserRegistry");
                throw new LoginException("Authentication infrastructure not available");
            }

            serviceReference = bundleContext.getServiceReference(UserRegistry.class);
            if (serviceReference == null) {
                logger.error("UserRegistry service reference is null");
                throw new LoginException("UserRegistry service not available");
            }

            userRegistry = bundleContext.getService(serviceReference);
            if (userRegistry == null) {
                logger.error("Cannot obtain UserRegistry service");
                throw new LoginException("UserRegistry service not available");
            }
        } catch (IllegalStateException e) {
            logger.error("Bundle context is no longer valid", e);
            throw new LoginException("Authentication infrastructure not available: " + e.getMessage());
        } catch (LoginException e) {
            // Re-throw LoginException as-is
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error initializing ManagedUserLoginModule", e);
            throw new LoginException("Authentication failed due to unexpected error: " + e.getMessage());
        }

        try {
            if (this.subject.getPrivateCredentials().isEmpty()) {
                logger.warn("No credentials provided in subject");
                throw new LoginException("No credentials provided");
            }

            Credentials credentials = (Credentials) this.subject.getPrivateCredentials().iterator().next();
            userRegistry.authenticate(credentials);
            return true;
        } catch (AuthenticationException e) {
            logger.debug("Authentication failed: {}", e.getMessage());
            throw new LoginException(e.getMessage());
        }
    }

    /**
     * Commits the authentication (phase 2 of JAAS two-phase commit).
     *
     * @return true to indicate successful commit
     * @throws LoginException if the commit fails
     */
    @Override
    public boolean commit() throws LoginException {
        // No additional state to commit in this simple implementation
        return true;
    }

    /**
     * Aborts the authentication attempt (rollback phase).
     * This is called if the overall authentication fails.
     *
     * @return true if abort succeeds
     * @throws LoginException if the abort fails
     */
    @Override
    public boolean abort() throws LoginException {
        cleanup();
        return true;
    }

    /**
     * Logs out the authenticated subject.
     *
     * @return true if logout succeeds
     * @throws LoginException if the logout fails
     */
    @Override
    public boolean logout() throws LoginException {
        cleanup();
        return true;
    }

    /**
     * Cleans up resources, including releasing the OSGi service reference.
     */
    private void cleanup() {
        if (bundleContext != null && serviceReference != null) {
            try {
                bundleContext.ungetService(serviceReference);
            } catch (IllegalStateException e) {
                logger.debug("Bundle context no longer valid during cleanup: {}", e.getMessage());
            }
            serviceReference = null;
        }
        userRegistry = null;
    }
}

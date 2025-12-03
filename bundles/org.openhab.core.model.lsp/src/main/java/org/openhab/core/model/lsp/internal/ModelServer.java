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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.model.script.ScriptServiceUtil;
import org.openhab.core.model.script.engine.ScriptEngine;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * A service component exposing a Language Server via sockets.
 *
 * @author Simon Kaufmann - Initial contribution
 */
@Component(immediate = true, service = ModelServer.class, configurationPid = ModelServer.CONFIGURATION_PID, //
        property = Constants.SERVICE_PID + "=org.openhab.lsp")
@ConfigurableService(category = "system", label = "Language Server (LSP)", description_uri = ModelServer.CONFIG_URI)
@NonNullByDefault
public class ModelServer {

    public static final String CONFIGURATION_PID = "org.openhab.lsp";
    protected static final String CONFIG_URI = "system:lsp";

    private static final String KEY_PORT = "port";
    private static final int DEFAULT_PORT = 5007;
    private final ExecutorService pool = ThreadPoolManager.getPool("lsp");

    private final Logger logger = LoggerFactory.getLogger(ModelServer.class);

    private @Nullable ServerSocket socket;

    private final Injector injector;

    /**
     * Constructs a new ModelServer with the specified dependencies.
     *
     * @param scriptServiceUtil the ScriptServiceUtil for script operations, must not be null
     * @param scriptEngine the ScriptEngine for script execution, must not be null
     */
    @Activate
    public ModelServer(final @Reference ScriptServiceUtil scriptServiceUtil,
            final @Reference ScriptEngine scriptEngine) {
        if (scriptServiceUtil == null) {
            throw new IllegalArgumentException("ScriptServiceUtil cannot be null");
        }
        if (scriptEngine == null) {
            throw new IllegalArgumentException("ScriptEngine cannot be null");
        }
        this.injector = Guice.createInjector(new RuntimeServerModule(scriptServiceUtil, scriptEngine));
    }

    /**
     * Activates the Language Server with the specified configuration.
     *
     * @param config the configuration map containing port settings, may be null
     */
    @Activate
    public void activate(Map<String, Object> config) {
        int port = DEFAULT_PORT;
        if (config != null && config.containsKey(KEY_PORT)) {
            try {
                Object portValue = config.get(KEY_PORT);
                if (portValue != null) {
                    port = Integer.parseInt(portValue.toString());
                    if (port < 1 || port > 65535) {
                        logger.warn("Port value '{}' is out of valid range (1-65535), using default port '{}'", port,
                                DEFAULT_PORT);
                        port = DEFAULT_PORT;
                    }
                }
            } catch (NumberFormatException e) {
                logger.warn("Couldn't parse '{}', using default port '{}' for the Language Server instead",
                        config.get(KEY_PORT), DEFAULT_PORT);
            }
        }
        final int serverPort = port;
        pool.submit(() -> listen(serverPort));
    }

    /**
     * Deactivates the Language Server and closes the server socket.
     */
    @Deactivate
    public void deactivate() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.debug("Language Server socket closed");
            }
        } catch (IOException e) {
            logger.error("Error shutting down the Language Server: {}", e.getMessage(), e);
        }
    }

    /**
     * Listens for client connections on the specified port.
     *
     * @param port the port number to listen on, must be between 1 and 65535
     */
    private void listen(int port) {
        try {
            socket = new ServerSocket(port);
            logger.info("Started Language Server Protocol (LSP) service on port {}", port);
            while (!socket.isClosed()) {
                logger.debug("Going to wait for a client to connect");
                try {
                    Socket client = socket.accept();
                    pool.submit(() -> handleConnection(client));
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        logger.error("Error accepting client connection: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error starting the Language Server", e);
        }
    }

    /**
     * Handles a client connection by creating a language server instance and processing LSP requests.
     *
     * @param client the client socket connection, must not be null
     */
    private void handleConnection(final Socket client) {
        if (client == null) {
            logger.warn("Cannot handle null client connection");
            return;
        }
        logger.debug("Client {} connected", client.getRemoteSocketAddress());
        try {
            LanguageServerImpl languageServer = injector.getInstance(LanguageServerImpl.class);
            if (languageServer == null) {
                logger.error("Failed to create LanguageServerImpl instance");
                return;
            }
            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(languageServer,
                    client.getInputStream(), client.getOutputStream());
            languageServer.connect(launcher.getRemoteProxy());
            Future<?> future = launcher.startListening();
            if (future != null) {
                future.get();
            }
        } catch (IOException e) {
            logger.warn("Error communicating with LSP client {}: {}", client.getRemoteSocketAddress(), e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("LSP client connection interrupted");
        } catch (ExecutionException e) {
            logger.error("Error running the Language Server: {}", e.getMessage(), e);
        } finally {
            try {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                logger.debug("Error closing client socket: {}", e.getMessage());
            }
        }
        logger.debug("Client {} disconnected", client.getRemoteSocketAddress());
    }
}

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
package org.openhab.core.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.utils.AudioStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an AudioStream from a URL. Note that some sinks, like Sonos, can directly handle URL
 * based streams, and therefore can/should call getURL() to get a direct reference to the URL.
 *
 * @author Karel Goderis - Initial contribution
 * @author Kai Kreuzer - Refactored to not require a source
 * @author Christoph Weitkamp - Refactored use of filename extension
 */
@NonNullByDefault
public class URLAudioStream extends AudioStream implements ClonableAudioStream {

    private static final Pattern PLS_STREAM_PATTERN = Pattern.compile("^File[0-9]=(.+)$");

    public static final String M3U_EXTENSION = "m3u";
    public static final String PLS_EXTENSION = "pls";

    private final Logger logger = LoggerFactory.getLogger(URLAudioStream.class);

    private final AudioFormat audioFormat;
    private final InputStream inputStream;
    private String url;

    private @Nullable Socket shoutCastSocket;

    public URLAudioStream(String url) throws AudioException {
        this.url = url;
        this.audioFormat = new AudioFormat(AudioFormat.CONTAINER_NONE, AudioFormat.CODEC_MP3, false, 16, null, null);
        this.inputStream = createInputStream();
    }

    private InputStream createInputStream() throws AudioException {
        final String filename = url.toLowerCase();
        final String extension = AudioStreamUtils.getExtension(filename);
        Socket socket = null;
        try {
            URL streamUrl = new URI(url).toURL();
            switch (extension) {
                case M3U_EXTENSION:
                    url = parseM3UPlaylist(streamUrl);
                    break;
                case PLS_EXTENSION:
                    url = parsePLSPlaylist(streamUrl);
                    break;
                default:
                    break;
            }
            streamUrl = new URI(url).toURL();
            URLConnection connection = streamUrl.openConnection();
            if ("unknown/unknown".equals(connection.getContentType())) {
                // Java does not parse non-standard headers used by SHOUTCast
                int port = streamUrl.getPort() > 0 ? streamUrl.getPort() : 80;
                // Manipulate User-Agent to receive a stream
                socket = new Socket(streamUrl.getHost(), port);
                shoutCastSocket = socket;

                OutputStream os = socket.getOutputStream();
                String userAgent = "WinampMPEG/5.09";
                String req = "GET / HTTP/1.0\r\nuser-agent: " + userAgent
                        + "\r\nIcy-MetaData: 1\r\nConnection: keep-alive\r\n\r\n";
                os.write(req.getBytes());
                return socket.getInputStream();
            } else {
                // getInputStream() method is more error-proof than openStream(),
                // because openStream() does openConnection().getInputStream(),
                // which opens a new connection and does not reuse the old one.
                return connection.getInputStream();
            }
        } catch (MalformedURLException | URISyntaxException e) {
            // Close socket if it was created but we're throwing an exception
            closeSocketQuietly(socket);
            logger.error("URL '{}' is not a valid url: {}", url, e.getMessage(), e);
            throw new AudioException("URL not valid");
        } catch (IOException e) {
            // Close socket if it was created but we're throwing an exception
            closeSocketQuietly(socket);
            logger.error("Cannot set up stream '{}': {}", url, e.getMessage(), e);
            throw new AudioException("IO Error");
        }
    }

    /**
     * Parse M3U playlist and extract the first non-comment URL.
     *
     * @param playlistUrl URL of the M3U playlist
     * @return The first valid stream URL found in the playlist
     * @throws IOException if the playlist cannot be read
     */
    private String parseM3UPlaylist(URL playlistUrl) throws IOException {
        try (Scanner scanner = new Scanner(playlistUrl.openStream(), StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    return line;
                }
            }
        }
        // If no valid URL found, return original URL
        return url;
    }

    /**
     * Parse PLS playlist and extract the first File entry.
     *
     * @param playlistUrl URL of the PLS playlist
     * @return The first valid stream URL found in the playlist
     * @throws IOException if the playlist cannot be read
     */
    private String parsePLSPlaylist(URL playlistUrl) throws IOException {
        try (Scanner scanner = new Scanner(playlistUrl.openStream(), StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.isEmpty() && line.startsWith("File")) {
                    final Matcher matcher = PLS_STREAM_PATTERN.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        }
        // If no valid URL found, return original URL
        return url;
    }

    /**
     * Safely close a socket without throwing exceptions.
     *
     * @param socket The socket to close, may be null
     */
    private void closeSocketQuietly(@Nullable Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Failed to close socket: {}", e.getMessage());
            }
        }
    }

    @Override
    public AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    public String getURL() {
        return url;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (shoutCastSocket instanceof Socket socket) {
            socket.close();
        }
    }

    @Override
    public String toString() {
        return url;
    }

    @Override
    public InputStream getClonedStream() throws AudioException {
        return new URLAudioStream(url);
    }
}

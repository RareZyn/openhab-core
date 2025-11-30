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
package org.openhab.core.io.rest.voice.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.auth.Role;
import org.openhab.core.io.rest.JSONResponse;
import org.openhab.core.io.rest.LocaleService;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.core.io.rest.voice.internal.dto.DialogStartParams;
import org.openhab.core.io.rest.voice.internal.dto.ListenAndAnswerParams;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.voice.*;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JSONRequired;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * This class acts as a REST resource for voice and language processing.
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Laurent Garnier - add TTS feature to the REST API
 * @author Markus Rathgeb - Migrated to JAX-RS Whiteboard Specification
 * @author Wouter Born - Migrated to OpenAPI annotations
 */
@Component
@JaxrsResource
@JaxrsName(VoiceResource.PATH_VOICE)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@Path(VoiceResource.PATH_VOICE)
@RolesAllowed({ Role.USER, Role.ADMIN })
@Tag(name = VoiceResource.PATH_VOICE)
@NonNullByDefault
public class VoiceResource implements RESTResource {

    /** The URI path to this resource */
    public static final String PATH_VOICE = "voice";

    private final Logger logger = LoggerFactory.getLogger(VoiceResource.class);

    private final LocaleService localeService;
    private final AudioManager audioManager;
    private final VoiceManager voiceManager;

    @Activate
    public VoiceResource( //
            final @Reference LocaleService localeService, //
            final @Reference AudioManager audioManager, //
            final @Reference VoiceManager voiceManager) {
        this.localeService = localeService;
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
    }

    /* --- INTERPRETER ENDPOINTS --- */

    @GET
    @Path("/interpreters")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getVoiceInterpreters", summary = "Get the list of all interpreters.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = HumanLanguageInterpreterDTO.class)))) })
    public Response getInterpreters(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language) {
        Locale locale = localeService.getLocale(language);
        List<HumanLanguageInterpreterDTO> dtos = voiceManager.getHLIs().stream().map(hli -> HLIMapper.map(hli, locale))
                .toList();
        return Response.ok(dtos).build();
    }

    @GET
    @Path("/interpreters/{id: [a-zA-Z_0-9]+}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getVoiceInterpreterByUID", summary = "Gets a single interpreter.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = HumanLanguageInterpreterDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Interpreter not found") })
    public Response getInterpreter(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language,
            @PathParam("id") @Parameter(description = "interpreter id") String id) {
        Locale locale = localeService.getLocale(language);
        HumanLanguageInterpreter hli = voiceManager.getHLI(id);
        if (hli == null)
            return notFound("No interpreter found");
        return Response.ok(HLIMapper.map(hli, locale)).build();
    }

    @POST
    @Path("/interpreters/{ids: [a-zA-Z_0-9,]+}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(operationId = "interpretText", summary = "Sends a text to a given human language interpreter(s).", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "No human language interpreter was found."),
            @ApiResponse(responseCode = "400", description = "interpretation exception occurs") })
    public Response interpret(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language,
            @Parameter(description = "text to interpret", required = true) String text,
            @PathParam("ids") @Parameter(description = "comma separated list of interpreter ids") List<String> ids) {
        Locale locale = localeService.getLocale(language);
        List<HumanLanguageInterpreter> hlis = voiceManager.getHLIsByIds(ids);
        if (hlis.isEmpty())
            return notFound("No interpreter found");

        String answer = "";
        String error = null;
        for (HumanLanguageInterpreter interpreter : hlis) {
            try {
                answer = interpreter.interpret(locale, text);
                logger.debug("Interpretation result: {}", answer);
                error = null;
                break;
            } catch (InterpretationException e) {
                logger.error("Interpretation exception: {}", e.getMessage());
                error = Objects.requireNonNullElse(e.getMessage(), "Unexpected error");
            }
        }
        return error != null ? badRequest(error) : Response.ok(answer, MediaType.TEXT_PLAIN).build();
    }

    @POST
    @Path("/interpreters")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(operationId = "interpretTextByDefaultInterpreter", summary = "Sends a text to the default human language interpreter.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "No human language interpreter was found."),
            @ApiResponse(responseCode = "400", description = "interpretation exception occurs") })
    public Response interpret(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language,
            @Parameter(description = "text to interpret", required = true) String text) {
        Locale locale = localeService.getLocale(language);
        HumanLanguageInterpreter hli = voiceManager.getHLI();
        if (hli == null)
            return notFound("No interpreter found");

        try {
            return Response.ok(hli.interpret(locale, text), MediaType.TEXT_PLAIN).build();
        } catch (InterpretationException e) {
            return badRequest(e.getMessage());
        }
    }

    @POST
    @Path("/interpreters/{ids: .+}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Interpret using one or more HLIs (comma-seperated IDs or 'default')")
    public Response interpretText(@HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Nullable String language,
            @PathParam("ids") String idsPath, @Parameter(description = "Text to interpret") String text) {

        if (text.isBlank())
            return badRequest("Text is required");

        Locale locale = localeService.getLocale(language);
        List<String> ids = "default".equalsIgnoreCase(idsPath) ? List.of()
                : Arrays.stream(idsPath.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        var result = InterpretationService.interpret(voiceManager, ids, locale, text, logger);
        return result.success() ? Response.ok(result.answer(), MediaType.TEXT_PLAIN).build()
                : badRequest(result.errorMessage());
    }

    /* --- VOICE ENDPOINTS --- */

    @GET
    @Path("/voices")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getVoices", summary = "Get the list of all voices.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = VoiceDTO.class)))) })
    public Response getVoices() {
        return Response.ok(voiceManager.getAllVoices().stream().map(VoiceMapper::map).toList()).build();
    }

    @GET
    @Path("/defaultvoice")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getDefaultVoice", summary = "Gets the default voice.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = VoiceDTO.class))),
            @ApiResponse(responseCode = "404", description = "No default voice was found.") })
    public Response getDefaultVoice() {
        Voice voice = voiceManager.getDefaultVoice();
        if (voice == null)
            return notFound("Default voice not found");
        return Response.ok(VoiceMapper.map(voice)).build();
    }

    @POST
    @Path("/say")
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(operationId = "textToSpeech", summary = "Speaks a given text with a given voice through the given audio sink.", responses = {
            @ApiResponse(responseCode = "200", description = "OK") })
    public Response say(@Parameter(description = "text to speak", required = true) String text,
            @QueryParam("voiceid") @Parameter(description = "voice id") @Nullable String voiceId,
            @QueryParam("sinkid") @Parameter(description = "audio sink id") @Nullable String sinkId,
            @QueryParam("volume") @Parameter(description = "volume level") @Nullable String volume) {
        PercentType volumePercent = (volume != null && !volume.isBlank()) ? new PercentType(volume) : null;
        voiceManager.say(text, voiceId, sinkId, volumePercent);
        return Response.ok(null, MediaType.TEXT_PLAIN).build();
    }

    /* --- DIALOG ENDPOINTS --- */

    @POST
    @Path("/dialog/start")
    @Operation(summary = "Start continuous voice dialog (support keyword spotting).", description = "Start a persistent voice dialog.")
    public Response startDialog(@BeanParam DialogStartParams params) {
        return DialogService.startDialog(voiceManager, audioManager, localeService, params, logger);
    }

    @POST
    @Path("/dialog/stop")
    @Operation(summary = "Stop active voice dialog.")
    public Response stopDialog(@QueryParam("sourceId") @Nullable String sourceId) {
        return DialogService.stopDialog(audioManager, voiceManager, sourceId, logger);
    }

    @POST
    @Path("/listenandanswer")
    @Operation(summary = "One-shot: listen -> interpret -> answer", description = "Non-persistent dialog: listen once, interprets, speaks response, then ends.")
    public Response listenAndAnswer(@BeanParam ListenAndAnswerParams params) {
        return DialogService.listenAndAnswer(voiceManager, audioManager, localeService, params, logger);
    }

    /* --- PRIVATE HELPERS --- */

    private Response notFound(@Nullable String message) {
        return JSONResponse.createErrorResponse(Status.NOT_FOUND, Objects.requireNonNullElse(message, "Not found"));
    }

    private Response badRequest(@Nullable String message) {
        return JSONResponse.createErrorResponse(Status.BAD_REQUEST, Objects.requireNonNullElse(message, "Bad request"));
    }
}

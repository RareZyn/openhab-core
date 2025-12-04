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
package org.openhab.core.io.rest.transform.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.Role;
import org.openhab.core.common.registry.RegistryChangedRunnableListener;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.core.io.rest.Stream2JSONInputStream;
import org.openhab.core.io.rest.transform.TransformationDTO;
import org.openhab.core.transform.ManagedTransformationProvider;
import org.openhab.core.transform.Transformation;
import org.openhab.core.transform.TransformationRegistry;
import org.openhab.core.transform.TransformationService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST resource that exposes transformations (list, single, add/update/delete)
 *
 * @author Jan N. Klug - Initial contribution
 */
@Component(immediate = true)
@JaxrsResource
@JaxrsName(TransformationResource.PATH_TRANSFORMATIONS)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@Path(TransformationResource.PATH_TRANSFORMATIONS)
@RolesAllowed({ Role.ADMIN })
@SecurityRequirement(name = "oauth2", scopes = { "admin" })
@Tag(name = TransformationResource.PATH_TRANSFORMATIONS)
@NonNullByDefault
public class TransformationResource implements RESTResource {
    public static final String PATH_TRANSFORMATIONS = "transformations";

    private final Logger logger = LoggerFactory.getLogger(TransformationResource.class);
    private final TransformationRegistry transformationRegistry;
    private final ManagedTransformationProvider managedTransformationProvider;
    private final BundleContext bundleContext;

    /**
     * Caches last modified date for GET list endpoint. AtomicReference used for thread-safe set/reset.
     * Reset when the registry changes.
     */
    private final AtomicReference<@Nullable Date> lastModifiedRef = new AtomicReference<>();

    private final RegistryChangedRunnableListener<Transformation> resetLastModifiedChangeListener = new RegistryChangedRunnableListener<>(
            () -> lastModifiedRef.set(null));

    @Nullable
    @Context
    private UriInfo uriInfo;

    @Activate
    public TransformationResource(final @Reference TransformationRegistry transformationRegistry,
            final @Reference ManagedTransformationProvider managedTransformationProvider,
            final BundleContext bundleContext) {
        this.transformationRegistry = Objects.requireNonNull(transformationRegistry, "transformationRegistry");
        this.managedTransformationProvider = Objects.requireNonNull(managedTransformationProvider,
                "managedTransformationProvider");
        this.bundleContext = Objects.requireNonNull(bundleContext, "bundleContext");

        this.transformationRegistry.addRegistryChangeListener(resetLastModifiedChangeListener);
    }

    @Deactivate
    void deactivate() {
        this.transformationRegistry.removeRegistryChangeListener(resetLastModifiedChangeListener);
    }

    /**
     * Returns a JSON array stream of all transformations.
     *
     * @param request JAX-RS request used for conditional processing
     * @return 200 OK with JSON array stream or 304 if not modified
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTransformations", summary = "Get a list of all transformations", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransformationDTO.class)))) })
    public Response getTransformations(@Context Request request) {
        logger.debug("Received HTTP GET by request at '{}'", Objects.requireNonNull(uriInfo).getPath());

        Date currentLastModified = lastModifiedRef.get();
        if (currentLastModified != null) {
            Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(currentLastModified);
            if (responseBuilder != null) {
                // send 304 Not Modified
                logger.debug("Transformation not modified since {}", currentLastModified);
                return responseBuilder.build();
            }
        } else {
            Date now = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            lastModifiedRef.compareAndSet(null, now);
            logger.debug("Initialized lastModified to {}", now);
        }

        Stream<TransformationDTO> stream = transformationRegistry.stream().map(TransformationDTO::new)
                .peek(dto -> dto.setEditable(isEditable(dto.getUid())));
        return Response.ok(new Stream2JSONInputStream(stream)).lastModified(lastModifiedRef.get())
                .cacheControl(RESTConstants.CACHE_CONTROL).build();
    }

    /**
     * Return all registered transformation service identifiers.
     *
     * @return JSON array of service names (String)
     */
    @GET
    @Path("services")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTransformationServices", summary = "Get all transformation services", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))) })
    public Response getTransformationServices() {
        try {
            Collection<ServiceReference<TransformationService>> refs = bundleContext
                    .getServiceReferences(TransformationService.class, null);

            // defensive: OSGi may return null or empty; guard against NPE
            Stream<String> services = (refs == null ? Stream.<ServiceReference<TransformationService>> empty()
                    : refs.stream()).map(ref -> (String) ref.getProperty(TransformationService.SERVICE_PROPERTY_NAME))
                    .filter(Objects::nonNull).map(Objects::requireNonNull).sorted();

            return Response.ok(new Stream2JSONInputStream(services)).build();
        } catch (InvalidSyntaxException e) {
            logger.error("Failed to query TransformationService references", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Return a single transformation by UID.
     *
     * @param uid transformation UID
     * @return 200 OK with DTO or 404 if not found
     */
    @GET
    @Path("{uid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTransformation", summary = "Get a single transformation", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Transformation.class))),
            @ApiResponse(responseCode = "404", description = "Not found") })
    public Response getTransformation(@PathParam("uid") @Parameter(description = "Transformation UID") String uid) {
        logger.debug("Received HTTP GET by uid '{}' at '{}'", uid, Objects.requireNonNull(uriInfo).getPath());

        Transformation transformation = transformationRegistry.get(uid);
        if (transformation == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        TransformationDTO dto = new TransformationDTO(transformation);
        dto.setEditable(isEditable(uid));
        return Response.ok(dto).build();
    }

    /**
     * Create or update transformation.
     *
     * @param uid path UID
     * @param newTransformation incoming DTO (may be null)
     * @return appropriate HTTP status
     */
    @PUT
    @Path("{uid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(operationId = "putTransformation", summary = "Put a single transformation", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request (content missing or invalid)"),
            @ApiResponse(responseCode = "405", description = "Transformation not editable") })
    public Response putTransformation(@PathParam("uid") @Parameter(description = "Transformation UID") String uid,
            @Parameter(description = "transformation", required = true) @Nullable TransformationDTO newTransformation) {
        logger.debug("Received HTTP PUT request for transformation '{}' at '{}'", uid,
                Objects.requireNonNull(uriInfo).getPath());

        Transformation oldTransformation = transformationRegistry.get(uid);
        if (oldTransformation != null && !isEditable(uid)) {
            logger.warn("Attempt to update non-editable transformation '{}'", uid);
            return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
        }

        if (newTransformation == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Content missing.").build();
        }

        if (!uid.equals(newTransformation.getUid())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("UID of transformation and path not matching.")
                    .build();
        }

        Transformation transformation = new Transformation(newTransformation.getUid(), newTransformation.getLabel(),
                newTransformation.getType(), newTransformation.getConfiguration());
        try {
            if (oldTransformation != null) {
                managedTransformationProvider.update(transformation);
                logger.info("Updated transformation '{}'", uid);
            } else {
                managedTransformationProvider.add(transformation);
                logger.info("Added transformation '{}'", uid);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Bad request while adding/updating transformation '{}': {}", uid, e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(Objects.requireNonNullElse(e.getMessage(), ""))
                    .build();
        }

        return Response.ok().build();
    }

    /**
     * Delete a transformation by UID if editable.
     *
     * @param uid transformation UID
     * @return 200 OK if deleted, 404 if not found, 405 if not editable
     */
    @DELETE
    @Path("{uid}")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(operationId = "deleteTransformation", summary = "Get a single transformation", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "UID not found"),
            @ApiResponse(responseCode = "405", description = "Transformation not editable") })
    public Response deleteTransformation(@PathParam("uid") @Parameter(description = "Transformation UID") String uid) {
        logger.debug("Received HTTP DELETE request for transformation '{}' at '{}'", uid,
                Objects.requireNonNull(uriInfo).getPath());

        Transformation oldTransformation = transformationRegistry.get(uid);
        if (oldTransformation == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!isEditable(uid)) {
            return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
        }

        managedTransformationProvider.remove(uid);
        logger.info("Removed transformation '{}'", uid);

        return Response.ok().build();
    }

    /**
     * Helper that determines whether a managed transformation with the given UID exist.
     *
     * @param uid the transformation UID
     * @return true if editable through ManagedTransformationProvider
     */
    private boolean isEditable(String uid) {
        return managedTransformationProvider.get(uid) != null;
    }
}

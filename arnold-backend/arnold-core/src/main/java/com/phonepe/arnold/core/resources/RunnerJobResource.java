/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.Roles;
import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import com.phonepe.arnold.core.runner.RunnerBackends;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.runner.ClaimRunnerJobRequest;
import com.phonepe.arnold.models.runner.RunnerJobResponse;
import com.phonepe.arnold.models.runner.RunnerJobUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Job lease protocol for runners that pull work from Arnold ({@code runner.mode} LOCAL or GITHUB).
 */
@Path("/runner/v1/jobs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
public class RunnerJobResource {

    private static final long POLL_INTERVAL_MS = 1000;

    private final RunnerBackends backends;

    @Inject
    public RunnerJobResource(final RunnerBackends backends) {
        this.backends = backends;
    }

    @POST
    @Path("/claim")
    @RolesAllowed(Roles.RUNNER_EXECUTE)
    @Operation(description = "Lease the next runnable job, or a specific job; response is null when none is available")
    public ArnoldResponse<RunnerJobResponse> claim(@Valid final ClaimRunnerJobRequest request)
            throws InterruptedException {
        final var queue = backends.queue();
        final var deadline = System.currentTimeMillis() + request.getWaitSeconds() * 1000L;
        while (true) {
            final var claimed = queue.claim(request.getWorkerId(), request.getJob());
            if (claimed.isPresent() || request.getJob() != null || System.currentTimeMillis() >= deadline) {
                return ArnoldResponse.ok(claimed.map(RunnerJobResource::toResponse).orElse(null));
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    @POST
    @Path("/{number}/heartbeat")
    @RolesAllowed(Roles.RUNNER_EXECUTE)
    @Operation(description = "Extend the lease on a running job")
    public ArnoldResponse<RunnerJobResponse> heartbeat(@Positive @PathParam("number") final long number,
                                                       @Valid final RunnerJobUpdateRequest request) {
        return ArnoldResponse.ok(toResponse(backends.queue().heartbeat(number, request.getWorkerId())));
    }

    @POST
    @Path("/{number}/complete")
    @RolesAllowed(Roles.RUNNER_EXECUTE)
    @Operation(description = "Record the outcome of a running job and release its lease")
    public ArnoldResponse<RunnerJobResponse> complete(@Positive @PathParam("number") final long number,
                                                      @Valid final RunnerJobUpdateRequest request) {
        return ArnoldResponse.ok(toResponse(backends.queue().complete(number, request.getWorkerId(),
                request.isSucceeded(), request.getDetail())));
    }

    private static RunnerJobResponse toResponse(final RunnerJob job) {
        return new RunnerJobResponse(job.number(), job.workflow(), job.variables(), job.status().name(),
                job.attempts(), job.leaseExpiresAt());
    }
}

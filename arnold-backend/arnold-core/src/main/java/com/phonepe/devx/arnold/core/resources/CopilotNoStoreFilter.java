package com.phonepe.devx.arnold.core.resources;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
public class CopilotNoStoreFilter implements ContainerResponseFilter {
    @Override
    public void filter(final ContainerRequestContext request, final ContainerResponseContext response) {
        if (request.getUriInfo().getPath().startsWith("copilot-integrations/v1/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
        }
    }
}

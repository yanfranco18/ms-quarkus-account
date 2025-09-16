package com.bancario.account.client;

import jakarta.ws.rs.*;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import io.smallrye.mutiny.Uni;
import com.bancario.account.dto.CustomerResponse;

@Path("/customers")
@RegisterRestClient(configKey = "customer-service")
public interface CustomerServiceRestClient {

    @GET
    @Path("/{customerId}")
    Uni<CustomerResponse> getCustomerById(@PathParam("customerId") String customerId);
}

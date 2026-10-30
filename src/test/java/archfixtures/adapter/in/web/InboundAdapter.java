package archfixtures.adapter.in.web;

import archfixtures.adapter.out.persistence.PublicAdapter;
import archfixtures.application.port.out.IThingStore;
import archfixtures.application.service.ServiceUsingAdapter;
import archfixtures.configuration.ThingConfiguration;

/** Violations: an inbound adapter using an outbound adapter, a configuration class, a service and an outgoing port. */
public class InboundAdapter {

    PublicAdapter outbound;
    ThingConfiguration configuration;
    ServiceUsingAdapter service;
    IThingStore store;
}

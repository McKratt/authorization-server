package archfixtures.domain;

import archfixtures.application.port.out.IThingStore;

/** Violation: the domain uses the application layer. */
public class DomainUsingPort {

    IThingStore store;
}

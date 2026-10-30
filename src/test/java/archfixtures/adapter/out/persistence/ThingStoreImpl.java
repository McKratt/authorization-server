package archfixtures.adapter.out.persistence;

import archfixtures.adapter.in.web.InboundAdapter;

/** Violations: an Impl suffix, and an outbound adapter using an inbound one. */
class ThingStoreImpl {

    InboundAdapter inbound;
}

package archfixtures.application.service;

import archfixtures.adapter.out.persistence.PublicAdapter;

/** Violation: an application service uses an adapter. */
public class ServiceUsingAdapter {

    PublicAdapter adapter;
}

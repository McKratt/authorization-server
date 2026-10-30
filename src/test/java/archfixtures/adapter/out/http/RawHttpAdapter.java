package archfixtures.adapter.out.http;

import java.net.http.HttpClient;

/** Violation: a raw HTTP client instead of Helidon's WebClient. */
class RawHttpAdapter {

    HttpClient client;
}

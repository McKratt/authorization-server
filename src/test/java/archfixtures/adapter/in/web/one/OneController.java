package archfixtures.adapter.in.web.one;

import archfixtures.adapter.in.web.two.TwoController;

/** Violation: one web adapter uses another. */
class OneController {

    TwoController other;
}

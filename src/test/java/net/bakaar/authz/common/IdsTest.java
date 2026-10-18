package net.bakaar.authz.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesUniquePositiveIds() {
        Set<Long> ids = new HashSet<>();
        LongStream.range(0, 100_000).forEach(i -> ids.add(Ids.next()));
        assertThat(ids).hasSize(100_000).allMatch(id -> id > 0);
    }

    @Test
    void idsAreTimeSorted() {
        long first = Ids.next();
        long second = Ids.next();
        assertThat(second).isGreaterThan(first);
    }
}

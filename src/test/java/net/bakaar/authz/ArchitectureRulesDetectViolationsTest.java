package net.bakaar.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Checks that the rules in {@link ArchitectureTest} really fail on bad code. The bad classes live in the test
 * sources (package archfixtures) and are not part of the analyzed application.
 */
class ArchitectureRulesDetectViolationsTest {

    private static final JavaClasses VIOLATIONS = new ClassFileImporter().importPackages("archfixtures");

    private static void assertViolation(ArchRule rule) {
        assertThat(rule.evaluate(VIOLATIONS).hasViolation())
                .as("Expected a violation for rule: " + rule.getDescription())
                .isTrue();
    }

    @Test
    void detectsViolationWhenTheDomainUsesHelidon() {
        assertViolation(ArchitectureTest.domainAndApplicationAreFrameworkFree);
    }

    @Test
    void detectsViolationWhenTheDomainUsesJson() {
        assertViolation(ArchitectureTest.domainAndApplicationHaveNoOrmOrJson);
    }

    @Test
    void detectsViolationWhenTheDomainUsesTheApplication() {
        assertViolation(ArchitectureTest.domainIsSelfContained);
    }

    @Test
    void detectsViolationWhenAServiceUsesAnAdapter() {
        assertViolation(ArchitectureTest.servicesDoNotUseAdapters);
        assertViolation(ArchitectureTest.applicationDoesNotDependOnAdapters);
    }

    @Test
    void detectsViolationWhenCommonUsesTheDomain() {
        assertViolation(ArchitectureTest.commonIsIndependent);
    }

    @Test
    void detectsViolationWhenAnInboundAdapterUsesAnOutboundAdapter() {
        assertViolation(ArchitectureTest.inboundAdaptersDoNotUseOutboundAdapters);
    }

    @Test
    void detectsViolationWhenAnInboundAdapterUsesAServiceOrAnOutgoingPort() {
        assertViolation(ArchitectureTest.inboundAdaptersUseOnlyIncomingPorts);
    }

    @Test
    void detectsViolationWhenAWebAdapterUsesAnother() {
        assertViolation(ArchitectureTest.webAdaptersAreIndependent);
    }

    @Test
    void detectsViolationWhenACommandIsOutsideIncomingPorts() {
        assertViolation(ArchitectureTest.commandsLiveInIncomingPorts);
    }

    @Test
    void detectsViolationWhenAnOutboundAdapterUsesAnInboundAdapter() {
        assertViolation(ArchitectureTest.outboundAdaptersDoNotUseInboundAdapters);
    }

    @Test
    void detectsViolationWhenAnAdapterUsesAConfigurationClass() {
        assertViolation(ArchitectureTest.onlyTheCompositionRootUsesConfiguration);
    }

    @Test
    void detectsViolationWhenAnAdapterIsPublic() {
        assertViolation(ArchitectureTest.adaptersArePackagePrivate);
    }

    @Test
    void detectsViolationWhenARawHttpClientIsUsed() {
        assertViolation(ArchitectureTest.noRawHttpClient);
    }

    @Test
    void detectsViolationWhenAPortIsNotAnInterface() {
        assertViolation(ArchitectureTest.portsAreInterfaces);
    }

    @Test
    void detectsViolationWhenAnInterfaceIsNamedWithAPortSuffixOrIPrefix() {
        assertViolation(ArchitectureTest.interfaceNamesFollowJavaConventions);
    }

    @Test
    void detectsViolationWhenAClassHasAnImplSuffix() {
        assertViolation(ArchitectureTest.noImplSuffixOnClasses);
    }
}

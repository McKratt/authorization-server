package net.bakaar.authz;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hexagonal architecture rules (hexagonal-helidon skill). {@link ArchitectureRulesDetectViolationsTest} checks that
 * every rule still fails on deliberately bad code.
 */
@AnalyzeClasses(packages = "net.bakaar.authz", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String[] ORM_AND_JSON = {
            "jakarta.persistence..", "javax.persistence..", "org.hibernate..",
            "org.eclipse.persistence..", "org.mybatis..",
            "com.fasterxml.jackson..", "tools.jackson..", "jakarta.json..", "com.google.gson..", "org.json.."};

    /**
     * Models and helpers the precompiled jte templates use. The templates are generated in {@code gg.jte.generated},
     * so these must be public (see ARCHITECTURE.md).
     */
    static final String[] TEMPLATE_MODELS = {
            "net.bakaar.authz.adapter.in.web.admin.AdminSession",
            "net.bakaar.authz.adapter.in.web.admin.ApplicationColors",
            "net.bakaar.authz.adapter.in.web.admin.FilterTexts",
            "net.bakaar.authz.adapter.in.web.admin.RoleInput"};

    // Lombok (lombok..) and JSpecify (org.jspecify..) are deliberately not forbidden in domain and application.
    @ArchTest
    static final ArchRule domainAndApplicationHaveNoOrmOrJson = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(ORM_AND_JSON);

    // The application services own the transaction boundary through an outgoing port (TransactionRunner).
    @ArchTest
    static final ArchRule domainAndApplicationAreFrameworkFree = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.helidon..", "jakarta.inject..", "jakarta.enterprise..", "jakarta.ws.rs..",
                    "org.springframework..", "java.sql..", "javax.sql..", "gg.jte..", "com.nimbusds..");

    @ArchTest
    static final ArchRule domainIsSelfContained = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");

    @ArchTest
    static final ArchRule servicesDoNotUseAdapters = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule commonIsIndependent = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain..", "..application..", "..adapter..", "..configuration..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    // Incoming adapters call use cases only: no application service, no outgoing port.
    @ArchTest
    static final ArchRule inboundAdaptersUseOnlyIncomingPorts = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application.service..", "..application.port.out..");

    // The authorization API and the admin UI share the application, not handlers.
    @ArchTest
    static final ArchRule webAdaptersAreIndependent = slices()
            .matching("..adapter.in.web.(*)..")
            .should().notDependOnEachOther();

    @ArchTest
    static final ArchRule commandsLiveInIncomingPorts = classes()
            .that().haveSimpleNameEndingWith("Command")
            .should().resideInAPackage("..application.port.in..");

    @ArchTest
    static final ArchRule outboundAdaptersDoNotUseInboundAdapters = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    /** Libraries have configuration packages of their own, e.g. org.flywaydb.core.api.configuration. */
    static final String[] LIBRARIES = {"java..", "javax..", "jakarta..", "com..", "org..", "io.."};

    // Without a container, the configuration classes are the composition root: only other configuration
    // classes and the main class may call them.
    @ArchTest
    static final ArchRule onlyTheCompositionRootUsesConfiguration = noClasses()
            .that().resideOutsideOfPackage("..configuration..")
            .and().haveSimpleNameNotEndingWith("Configuration")
            .and().doNotHaveFullyQualifiedName("net.bakaar.authz.AuthorizationServerApplication")
            .should().dependOnClassesThat(resideInAPackage("..configuration..")
                    .and(resideOutsideOfPackages(LIBRARIES)))
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("AdapterConfiguration");

    // Each adapter package exposes one public *AdapterConfiguration that creates its adapters, so every
    // adapter class stays package-private, except the models the jte templates need.
    @ArchTest
    static final ArchRule adaptersArePackagePrivate = classes()
            .that().resideInAPackage("..adapter..")
            .and().areTopLevelClasses()
            .and().haveSimpleNameNotEndingWith("AdapterConfiguration")
            .and().doNotHaveFullyQualifiedName(TEMPLATE_MODELS[0])
            .and().doNotHaveFullyQualifiedName(TEMPLATE_MODELS[1])
            .and().doNotHaveFullyQualifiedName(TEMPLATE_MODELS[2])
            .and().doNotHaveFullyQualifiedName(TEMPLATE_MODELS[3])
            .should().notBePublic();

    // Outgoing HTTP goes through Helidon's WebClient, never through a raw HTTP client.
    @ArchTest
    static final ArchRule noRawHttpClient = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "org.apache.hc..", "okhttp3..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.HttpURLConnection");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("..application.port..")
            .and().areNotAssignableTo(Record.class)
            .and().doNotHaveSimpleName("package-info")
            .should().beInterfaces();

    // Interface names follow the usual Java conventions (https://www.baeldung.com/java-interface-naming-conventions):
    // a noun or an adjective, no "I" prefix, no "Impl" suffix, and no Port / UseCase suffix.
    @ArchTest
    static final ArchRule interfaceNamesFollowJavaConventions = noClasses()
            .that().areInterfaces()
            .should().haveSimpleNameEndingWith("Port")
            .orShould().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Impl")
            .orShould().haveNameMatching(".*[.]I[A-Z][^.]*");

    @ArchTest
    static final ArchRule noImplSuffixOnClasses = noClasses()
            .should().haveSimpleNameEndingWith("Impl");
}

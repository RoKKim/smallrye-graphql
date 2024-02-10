package io.smallrye.graphql.bootstrap;

import static com.apollographql.federation.graphqljava.FederationDirectives.loadFederationSpecDefinitions;

import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.apollographql.federation.graphqljava.directives.LinkDirectiveProcessor;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;

import graphql.language.DirectiveDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.smallrye.graphql.scalar.federation.ImportCoercing;
import io.smallrye.graphql.schema.model.DirectiveInstance;
import io.smallrye.graphql.schema.model.Schema;
import io.smallrye.graphql.spi.config.Config;

/**
 * This class is roughly based on the
 * {@link LinkDirectiveProcessor#loadFederationImportedDefinitions(TypeDefinitionRegistry)} method, but since it
 * only accepts {@link TypeDefinitionRegistry} as an argument, it is not directly in our case.
 *
 * @see <a href="https://specs.apollo.dev/link/v1.0/#Import">link v1.0</a>
 */
public class LinkProcessor {

    private String specUrl;

    private final Schema schema;
    private final Map<String, String> specImports;
    private final Map<String, String> imports;
    private final List<String> federationSpecVersionImports;

    private static final Pattern FEDERATION_VERSION_PATTERN = Pattern.compile("/v([\\d.]+)$");
    private static final Map<String, String> FEDERATION_DIRECTIVES_VERSION = Map.of(
            "@composeDirective", "2.1",
            "@interfaceObject", "2.4",
            "@authenticated", "2.5",
            "@requiresScopes", "2.5",
            "@policy", "2.6");
    private static final ImportCoercing IMPORT_COERCING = new ImportCoercing();

    public LinkProcessor(Schema schema) {
        this.schema = schema;
        this.imports = new LinkedHashMap<>();
        this.specImports = new LinkedHashMap<>();
        this.federationSpecVersionImports = new ArrayList<>();
    }

    public void createLinkImports() {
        if (Config.get().isFederationEnabled()) {
            List<DirectiveInstance> specLinkDirectives = new ArrayList<>();
            List<DirectiveInstance> linkDirectives = new ArrayList<>();

            schema.getDirectiveInstances().stream()
                    .filter(directiveInstance -> "link".equals(directiveInstance.getType().getName()))
                    .filter(directiveInstance -> {
                        Map<String, Object> values = directiveInstance.getValues();
                        return values.containsKey("url") && values.get("url") instanceof String;
                    })
                    .forEach(directiveInstance -> {
                        String url = (String) directiveInstance.getValues().get("url");
                        if (url.startsWith("https://specs.apollo.dev/federation/")) {
                            specLinkDirectives.add(directiveInstance);
                        } else {
                            linkDirectives.add(directiveInstance);
                        }
                    });

            createSpecLinkImports(specLinkDirectives);
            createLinkImports(linkDirectives);
        }
    }

    private void createSpecLinkImports(List<DirectiveInstance> linkDirectives) {
        if (linkDirectives.isEmpty()) {
            return;
        }
        // We can only have a single Federation spec link
        if (linkDirectives.size() > 1) {
            String directivesString = linkDirectives.stream()
                    .map(DirectiveInstance::toString)
                    .collect(Collectors.joining(", "));
            throw new RuntimeException(
                    "Multiple @link directives that import Federation spec found on schema: " + directivesString);
        }

        DirectiveInstance linkDirective = linkDirectives.get(0);
        specUrl = (String) linkDirective.getValues().get("url");
        validateNamespace(linkDirective, specUrl);

        String federationVersion = extractFederationVersion(specUrl);
        // We only support Federation 2.0
        if (isVersionGreaterThan("2.0", federationVersion)) {
            throw new UnsupportedFederationVersionException(specUrl);
        }

        processImports((Object[]) linkDirective.getValues().get("import"), specImports);
        for (Map.Entry<String, String> directiveInfo : FEDERATION_DIRECTIVES_VERSION.entrySet()) {
            validateDirectiveSupport(specImports, federationVersion, directiveInfo.getKey(),
                    directiveInfo.getValue());
        }

        // Based on the Federation spec URL, we load the definitions and save them to a separate list
        processFederationSpecImports(specUrl, federationSpecVersionImports);
        if (!federationSpecVersionImports.contains("@link")) {
            // @link is not allowed to be imported
            throw new RuntimeException("Import key @link should not be imported within @link directive itself");
        }
    }

    private void createLinkImports(List<DirectiveInstance> linkDirectives) {
        for (DirectiveInstance linkDirective : linkDirectives) {
            processImports((Object[]) linkDirective.getValues().get("import"), imports);
        }
    }

    private void validateNamespace(DirectiveInstance linkDirective, String specUrl) {
        if (linkDirective.getValues().get("as") != null) {
            String namespace = (String) linkDirective.getValues().get("as");
            // Check the format of the as argument, as per the documentation
            if (namespace.startsWith("@")) {
                throw new RuntimeException(String.format(
                        "Argument as %s for Federation spec %s on @link directive must not start with '@'", namespace,
                        specUrl));
            }
            if (namespace.contains("__")) {
                throw new RuntimeException(String.format(
                        "Argument as %s for Federation spec %s on @link directive must not contain the namespace " +
                                "separator '__'",
                        namespace, specUrl));
            }
            if (namespace.endsWith("_")) {
                throw new RuntimeException(String.format(
                        "Argument as %s for Federation spec %s on @link directive must not end with an underscore",
                        namespace, specUrl));
            }
        }
    }

    private boolean isVersionGreaterThan(String version1, String version2) {
        ModuleDescriptor.Version v1 = ModuleDescriptor.Version.parse(version1);
        ModuleDescriptor.Version v2 = ModuleDescriptor.Version.parse(version2);
        return v1.compareTo(v2) > 0;
    }

    private String extractFederationVersion(String specUrl) {
        try {
            Matcher matcher = FEDERATION_VERSION_PATTERN.matcher(specUrl);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new UnsupportedFederationVersionException(specUrl);
            }
        } catch (Exception e) {
            throw new UnsupportedFederationVersionException(specUrl);
        }
    }

    private void processImports(Object[] importsArray, Map<String, String> imports) {
        if (importsArray == null) {
            return;
        }
        for (Object _import : importsArray) {
            Object parsedImport = IMPORT_COERCING.parseValue(_import);
            if (parsedImport == null)
                continue;
            String importName;
            if (parsedImport instanceof String) {
                importName = (String) parsedImport;
                imports.put(importName, importName);
            } else if (parsedImport instanceof Map) {
                Map<?, ?> importMap = (Map<?, ?>) parsedImport;
                importName = (String) importMap.get("name");
                String importAs = (String) importMap.get("as");

                // name and as must be of the same type
                if ((importName.startsWith("@") && !importAs.startsWith("@")) ||
                        (!importName.startsWith("@") && importAs.startsWith("@"))) {
                    throw new RuntimeException(
                            String.format(
                                    "Import name '%s' and alias '%s' on on @link directive must be of the same type: " +
                                            "either both directives or both types.",
                                    importName, importAs));
                }

                imports.put(importName, importAs);
            }
        }
    }

    private void validateDirectiveSupport(Map<String, String> imports, String version, String directiveName,
            String minVersion) {
        if (imports.containsKey(directiveName) && isVersionGreaterThan(minVersion, version)) {
            throw new RuntimeException(
                    String.format("Federation v%s feature %s imported using old Federation v%s version", minVersion,
                            directiveName, version));
        }
    }

    private void processFederationSpecImports(String specUrl, List<String> imports) {
        imports.addAll(
                loadFederationSpecDefinitions(specUrl).stream()
                        .map(definition -> definition instanceof DirectiveDefinition ? "@" + definition.getName()
                                : definition.getName())
                        .collect(Collectors.toList()));
    }

    public String newNameDirectiveFrom(String name) {
        String key = "@" + name;
        if (imports.containsKey(key)) {
            return newNameDirective(name);
        }
        // If directive is used and defined by the Federation spec, it must also be imported inside @link
        if (specUrl != null && !specImports.containsKey(key) && federationSpecVersionImports.contains(key) &&
                !key.equals("@link")) {
            throw new RuntimeException(
                    String.format("Directive '%s' is used but not imported inside @link directive", key));
        }
        return newNameDirective(name);
    }

    public String newNameDirective(String name) {
        return newName(name, true);
    }

    public String newName(String name) {
        return newName(name, false);
    }

    private String newName(String name, boolean isDirective) {
        if (!Config.get().isFederationEnabled()) {
            return name;
        }

        String key = isDirective ? "@" + name : name;
        if (imports.containsKey(key)) {
            // Our type can be imported using non-Federation spec @link
            String newName = imports.get(key);
            return isDirective ? newName.substring(1) : newName;
        } else if (federationSpecVersionImports.contains(key) && !key.equals("@link")) {
            // We only wish to rename the types that are defined by the Federation spec (e.g. @key, @external etc.),
            // but not common and custom types like String, BigInteger, BigDecimal etc. We also don't want to rename
            // the @link directive.
            if (specImports.containsKey(key)) {
                String newName = specImports.get(key);
                return isDirective ? newName.substring(1) : newName;
            } else {
                if (name.equals("Import") || name.equals("Purpose")) {
                    return "link__" + name;
                } else {
                    // Apply default namespace
                    return "federation__" + name;
                }
            }
        } else {
            return name;
        }
    }
}

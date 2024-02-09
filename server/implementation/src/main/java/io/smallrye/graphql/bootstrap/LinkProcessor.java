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
 * @see <a href="https://specs.apollo.dev/link/v1.0/#Import">link v1.0</a>
 */
public class LinkProcessor {

    private String specUrl;
    private String namespace;

    private final Map<String, String> imports;
    private final List<String> federationVersionImports;

    private static final Pattern FEDERATION_VERSION_PATTERN = Pattern.compile("/v([\\d.]+)$");
    private static final Map<String, String> FEDERATION_DIRECTIVES_VERSION = Map.of(
            "@composeDirective", "2.1",
            "@interfaceObject", "2.4",
            "@authenticated", "2.5",
            "@requiresScopes", "2.5",
            "@policy", "2.6");

    public LinkProcessor() {
        this.imports = new LinkedHashMap<>();
        this.federationVersionImports = new ArrayList<>();
    }

    /**
     * This method is roughly based on the
     * {@link LinkDirectiveProcessor#loadFederationImportedDefinitions(TypeDefinitionRegistry)} method, but since it
     * only accepts {@link TypeDefinitionRegistry} as an argument, it is not directly usable here.
     */
    public void createLinkImportedTypes(Schema schema) {
        // todo RokM probably need to set this to system property
        if (Config.get().isFederationEnabled()) {
            List<DirectiveInstance> linkDirectives = schema.getDirectiveInstances().stream()
                    .filter(directiveInstance -> "link".equals(directiveInstance.getType().getName()))
                    .filter(directiveInstance -> {
                        Map<String, Object> values = directiveInstance.getValues();
                        if (values.containsKey("url") && values.get("url") instanceof String) {
                            String url = (String) values.get("url");
                            // Find urls that are defining the Federation spec
                            return url.startsWith("https://specs.apollo.dev/federation/");
                        }
                        return false;
                    })
                    .collect(Collectors.toList());

            // We can only have a single Federation spec link
            if (linkDirectives.size() > 1) {
                String directivesString = linkDirectives.stream()
                        .map(DirectiveInstance::toString)
                        .collect(Collectors.joining(", "));
                throw new RuntimeException("Multiple 'link' directives found on schema: " + directivesString);
            }

            if (!linkDirectives.isEmpty()) {
                DirectiveInstance linkDirective = linkDirectives.get(0);
                specUrl = (String) linkDirective.getValues().get("url");
                final String federationVersion;
                try {
                    Matcher matcher = FEDERATION_VERSION_PATTERN.matcher(specUrl);
                    if (matcher.find()) {
                        federationVersion = matcher.group(1);
                    } else {
                        throw new UnsupportedFederationVersionException(specUrl);
                    }
                } catch (Exception e) {
                    throw new UnsupportedFederationVersionException(specUrl);
                }
                // We only support Federation 2.0
                if (isVersionGreaterThan("2.0", federationVersion)) {
                    throw new UnsupportedFederationVersionException(specUrl);
                }

                if (linkDirective.getValues().get("as") != null) {
                    namespace = (String) linkDirective.getValues().get("as");
                    // Check the format of the as argument, as per the documentation
                    if (namespace.startsWith("@")) {
                        throw new RuntimeException(String.format(
                                "Argument as %s for Federation spec %s must not start with '@'", namespace, specUrl));
                    }
                    if (namespace.contains("__")) {
                        throw new RuntimeException(String.format(
                                "Argument as %s for Federation spec %s must not contain the namespace separator '__'",
                                namespace, specUrl));
                    }
                    if (namespace.endsWith("_")) {
                        throw new RuntimeException(String.format(
                                "Argument as %s for Federation spec %s must not end with an underscore", namespace,
                                specUrl));
                    }
                }

                // Based on the Federation spec URL, we load the definitions and save them to a separate list
                federationVersionImports.addAll(
                        loadFederationSpecDefinitions(specUrl).stream()
                                .map(definition -> definition instanceof DirectiveDefinition ? "@" + definition.getName()
                                        : definition.getName())
                                .collect(Collectors.toList()));

                ImportCoercing importCoercing = new ImportCoercing();
                for (Object _import : (Object[]) linkDirective.getValues().get("import")) {
                    Object importValue = importCoercing.parseValue(_import);
                    if (importValue != null) {
                        String importName = null;
                        if (importValue instanceof String) {
                            importName = (String) importValue;
                            imports.put(importName, importName);
                        } else if (importValue instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) importValue;
                            importName = (String) map.get("name");
                            String importAs = (String) map.get("as");

                            // name and as must be of the same type
                            if ((importName.startsWith("@") && !importAs.startsWith("@")) ||
                                    (!importName.startsWith("@") && importAs.startsWith("@"))) {
                                throw new RuntimeException(
                                        String.format("Import name '%s' and alias '%s' must be of the same type: " +
                                                "either both directives or both types.", importName, importAs));
                            }

                            imports.put(importName, importAs);
                        }
                        if (!federationVersionImports.contains(importName)) {
                            // If a key is found in imports but not in federationVersionImports, throw an exception
                            throw new RuntimeException(
                                    String.format("Import key '%s' is not present in the Federation spec %s",
                                            importName, specUrl));
                        }
                    }
                }
                if (!federationVersionImports.contains("@link")) {
                    // @link is not allowed to be imported
                    throw new RuntimeException("Import key '@link' should not be imported");
                }
                for (Map.Entry<String, String> directiveInfo : FEDERATION_DIRECTIVES_VERSION.entrySet()) {
                    validateDirectiveSupport(imports, federationVersion, directiveInfo.getKey(),
                            directiveInfo.getValue());
                }
            }
        }
    }

    private void validateDirectiveSupport(Map<String, String> imports, String version, String directiveName,
            String minVersion) {
        if (imports.containsKey(directiveName) && isVersionGreaterThan(minVersion, version)) {
            throw new RuntimeException(String.format("Federation v%s feature %s imported using old Federation v%s " +
                    "version", minVersion, directiveName, version));
        }
    }

    private boolean isVersionGreaterThan(String version1, String version2) {
        ModuleDescriptor.Version v1 = ModuleDescriptor.Version.parse(version1);
        ModuleDescriptor.Version v2 = ModuleDescriptor.Version.parse(version2);
        return v1.compareTo(v2) > 0;
    }

    public String newNameDirectiveFrom(String name) {
        String key = "@" + name;
        // If directive is used it must also be imported inside @link
        if (specUrl != null && !imports.containsKey(key)) {
            throw new RuntimeException(String.format("Directive '%s' is used but not imported", name));
        }
        return newName(key, true);
    }

    public String newNameDirective(String name) {
        String key = "@" + name;
        return newName(key, true);
    }

    public String newName(String name, boolean isDirective) {
        if (specUrl != null) {
            String key = isDirective ? "@" + name : name;

            // We only wish to rename the types that are defined by the Federation spec (e.g. @key, @external etc.),
            // but not common and custom types like String, BigInteger, BigDecimal etc.
            // We also don't want to rename the @link directive.
            if (!federationVersionImports.contains(key) || key.equals("@link")) {
                return name;
            }

            if (imports.containsKey(key)) {
                String newName = imports.get(key);
                if (isDirective) {
                    return newName.substring(1);
                } else {
                    return newName;
                }
            } else {
                if (name.equals("Import") || name.equals("Purpose")) {
                    return "link__" + name;
                } else {
                    // apply default namespace
                    return "federation__" + name;
                }
            }
        } else {
            return name;
        }
    }
}

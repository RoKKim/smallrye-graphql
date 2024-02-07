package io.smallrye.graphql.bootstrap;

import java.lang.module.ModuleDescriptor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.apollographql.federation.graphqljava.directives.LinkDirectiveProcessor;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.smallrye.graphql.scalar.federation.ImportCoercing;
import io.smallrye.graphql.schema.model.DirectiveInstance;
import io.smallrye.graphql.schema.model.Schema;
import io.smallrye.graphql.spi.config.Config;

public class LinkProcessor {

    private String specUrl;
    private String namespace;
    private final Map<String, String> imports = new LinkedHashMap<>();

    private static final Pattern FEDERATION_VERSION_PATTERN = Pattern.compile("/v([\\d.]+)$");
    private static final Map<String, String> FEDERATION_DIRECTIVES_VERSION = Map.of(
            "@composeDirective", "2.1",
            "@interfaceObject", "2.4",
            "@authenticated", "2.5",
            "@requiresScopes", "2.5",
            "@policy", "2.6");
    private static final Set<String> BUILT_IN_SCALARS = new HashSet<>(
            Arrays.asList("String", "Boolean", "Int", "Float", "ID"));

    public String getSpecUrl() {
        return specUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, String> getImports() {
        return imports;
    }

    /**
     * This method is roughly based on the
     * {@link LinkDirectiveProcessor#loadFederationImportedDefinitions(TypeDefinitionRegistry)} method, but since it
     * only accepts {@link TypeDefinitionRegistry} as an argument, it is not directly usable here.
     */
    public void createLinkImportedTypes(Schema schema) {
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
            throw new RuntimeException("Multiple \"link\" directives found on schema: " + directivesString);
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

            ImportCoercing importCoercing = new ImportCoercing();
            Map<String, String> imports = new LinkedHashMap<>();
            for (Object _import : (Object[]) linkDirective.getValues().get("import")) {
                Object importValue = importCoercing.parseValue(_import);
                if (importValue != null) {
                    if (importValue instanceof String) {
                        imports.put((String) importValue, (String) importValue);
                    } else if (importValue instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) importValue;
                        imports.put((String) map.get("name"), (String) map.get("as"));
                    }
                }
            }
            for (Map.Entry<String, String> directiveInfo : FEDERATION_DIRECTIVES_VERSION.entrySet()) {
                validateDirectiveSupport(imports, federationVersion, directiveInfo.getKey(), directiveInfo.getValue());
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

    public String newName(String name, boolean isDirective) {
        if (Config.get().isFederationEnabled()) {
            String key;
            if (isDirective) {
                key = "@" + name;
            } else {
                key = name;
                if (BUILT_IN_SCALARS.contains(key)) {
                    // We do not want to rename built-in types
                    return name;
                }
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

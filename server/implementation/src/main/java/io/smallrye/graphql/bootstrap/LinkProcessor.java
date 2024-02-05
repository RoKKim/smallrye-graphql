package io.smallrye.graphql.bootstrap;

import com.apollographql.federation.graphqljava.directives.LinkDirectiveProcessor;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedLinkImportException;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.smallrye.graphql.schema.model.DirectiveInstance;
import io.smallrye.graphql.schema.model.Schema;
import io.smallrye.graphql.spi.config.Config;
import org.jboss.jandex.AnnotationInstance;

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

public class LinkProcessor {

    private String specUrl;
    private String namespace;
    private final Map<String, String> imports = new LinkedHashMap<>();

    private static final Pattern FEDERATION_VERSION_PATTERN = Pattern.compile("/v([\\d.]+)$");
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

            Object[] importAnnotations = (Object[]) linkDirective.getValues().get("import");
            // Parse imports based on name and as arguments
            for (Object annotation : importAnnotations) {
                AnnotationInstance annotationInstance = (AnnotationInstance) annotation;
                String importName = (String) annotationInstance.value("name").value();
                String importAs = importName;
                if (annotationInstance.value("as") != null) {
                    importAs = (String) annotationInstance.value("as").value();
                    if (importName.startsWith("@") && !importAs.startsWith("@")) {
                        throw new RuntimeException(String.format(
                                "Directive import %s for Federation spec %s starts with '@' so as %s must also start " +
                                        "with '@'",
                                importName, specUrl, importAs));
                    }
                }
                imports.put(importName, importAs);
            }

            // We only support Federation 2.0
            if (isVersionGreaterThan("2.0", federationVersion)) {
                throw new UnsupportedFederationVersionException(specUrl);
            }
            if (imports.containsKey("@composeDirective") && isVersionGreaterThan("2.1", federationVersion)) {
                throw new UnsupportedLinkImportException("@composeDirective");
            }
            if (imports.containsKey("@interfaceObject") && isVersionGreaterThan("2.3", federationVersion)) {
                throw new UnsupportedLinkImportException("@interfaceObject");
            }
            if (imports.containsKey("@authenticated") && isVersionGreaterThan("2.5", federationVersion)) {
                throw new UnsupportedLinkImportException("@authenticated");
            }
            if (imports.containsKey("@requiresScopes") && isVersionGreaterThan("2.5", federationVersion)) {
                throw new UnsupportedLinkImportException("@requiresScopes");
            }
            if (imports.containsKey("@policy") && isVersionGreaterThan("2.6", federationVersion)) {
                throw new UnsupportedLinkImportException("@policy");
            }
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

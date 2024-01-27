package io.smallrye.graphql.schema.helper;

import static java.util.stream.Collectors.toList;
import static org.jboss.jandex.AnnotationValue.Kind.ARRAY;

import java.util.*;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.smallrye.graphql.api.federation.policy.Policy;
import io.smallrye.graphql.api.federation.requiresscopes.RequiresScopes;
import io.smallrye.graphql.schema.Annotations;
import io.smallrye.graphql.schema.model.DirectiveInstance;
import io.smallrye.graphql.schema.model.DirectiveType;

public class Directives {

    // Directives generated from application annotations that have a `@Directive` on them.
    // These directive types are expected to have the `className` field defined
    private final Map<DotName, DirectiveType> directiveTypes;

    // Other directive types - for example, directives from bean validation constraints.
    private final List<DirectiveType> directiveTypesOther;

    private static final Logger LOG = Logger.getLogger(Directives.class.getName());

    public Directives(List<DirectiveType> directiveTypes) {
        // not with streams/collector, so duplicate keys are allowed and overwritten
        this.directiveTypes = new HashMap<>();
        this.directiveTypesOther = new ArrayList<>();
        for (DirectiveType directiveType : directiveTypes) {
            if (directiveType.getClassName() != null) {
                this.directiveTypes.put(DotName.createSimple(directiveType.getClassName()), directiveType);
            } else {
                this.directiveTypesOther.add(directiveType);
            }
        }
    }

    public List<DirectiveInstance> buildDirectiveInstances(Annotations annotations, String directiveLocation,
            String referenceName) {
        // only build directive instances from `@Directive` annotations here (that means the `directiveTypes` map),
        // because `directiveTypesOther` directives get their instances added on-the-go by classes that extend `ModelCreator`
        return directiveTypes.keySet().stream()
                .flatMap(annotations::resolve)
                .map(this::toDirectiveInstance)
                .filter(directiveInstance -> {
                    if (!directiveInstance.getType().getLocations().contains(directiveLocation)) {
                        LOG.warnf(
                                "Directive instance: '%s' assigned to '%s' cannot be applied." +
                                        " The directive is allowed on locations '%s' but on '%s'",
                                directiveInstance.getType().getClassName(), referenceName,
                                directiveInstance.getType().getLocations(), directiveLocation);
                        return false;
                    }
                    return true;
                })
                .collect(toList());
    }

    private DirectiveInstance toDirectiveInstance(AnnotationInstance annotationInstance) {
        DirectiveInstance directiveInstance = new DirectiveInstance();
        DirectiveType directiveType = directiveTypes.get(annotationInstance.name());
        directiveInstance.setType(directiveType);

        Class<?> directiveClass;
        try {
            directiveClass = Class.forName(directiveType.getClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class for directive: " + directiveType.getClassName(), e);
        }

        for (AnnotationValue annotationValue : annotationInstance.values()) {
            if (RequiresScopes.class.isAssignableFrom(directiveClass) || Policy.class.isAssignableFrom(
                    directiveClass)) {
                // For both of these directives, we need to process the annotation values as nested arrays of strings
                List<List<String>> valueList = processAnnotationValues((AnnotationValue[]) annotationValue.value());
                directiveInstance.setValue(annotationValue.name(), valueList);
            } else {
                directiveInstance.setValue(annotationValue.name(), valueObject(annotationValue));
            }
        }

        return directiveInstance;
    }

    private Object valueObject(AnnotationValue annotationValue) {
        if (annotationValue.kind() == ARRAY) {
            AnnotationValue[] values = (AnnotationValue[]) annotationValue.value();
            Object[] objects = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                objects[i] = valueObject(values[i]);
            }
            return objects;
        }
        return annotationValue.value();
    }

    public Map<DotName, DirectiveType> getDirectiveTypes() {
        return directiveTypes;
    }

    private List<List<String>> processAnnotationValues(AnnotationValue[] annotationValues) {
        if (annotationValues == null || annotationValues.length == 0) {
            return Collections.emptyList();
        }

        List<List<String>> valuesList = new ArrayList<>();
        for (AnnotationValue nestedValue : annotationValues) {
            List<String> values = new ArrayList<>();
            for (AnnotationValue value : (AnnotationValue[]) nestedValue.asNested().values().get(0).value()) {
                values.add(value.asString());
            }
            valuesList.add(values);
        }
        return valuesList;
    }
}

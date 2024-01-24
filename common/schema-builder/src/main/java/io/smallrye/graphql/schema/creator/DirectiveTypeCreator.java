package io.smallrye.graphql.schema.creator;

import static io.smallrye.graphql.schema.Annotations.DIRECTIVE;
import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.smallrye.graphql.api.federation.policy.Policy;
import io.smallrye.graphql.api.federation.requiresscopes.RequiresScopes;
import io.smallrye.graphql.schema.Annotations;
import io.smallrye.graphql.schema.helper.DescriptionHelper;
import io.smallrye.graphql.schema.helper.Direction;
import io.smallrye.graphql.schema.helper.TypeNameHelper;
import io.smallrye.graphql.schema.model.DirectiveArgument;
import io.smallrye.graphql.schema.model.DirectiveType;

public class DirectiveTypeCreator extends ModelCreator {
    private static final Logger LOG = Logger.getLogger(DirectiveTypeCreator.class.getName());

    public DirectiveTypeCreator(ReferenceCreator referenceCreator) {
        super(referenceCreator);
    }

    @Override
    public String getDirectiveLocation() {
        throw new IllegalArgumentException(
                "This method should never be called since 'DirectiveType' cannot have another directives");
    }

    private static DotName NON_NULL = DotName.createSimple("org.eclipse.microprofile.graphql.NonNull");

    public DirectiveType create(ClassInfo classInfo) {
        LOG.debug("Creating directive from " + classInfo.name().toString());

        Annotations annotations = Annotations.getAnnotationsForClass(classInfo);

        DirectiveType directiveType = new DirectiveType();
        directiveType.setClassName(classInfo.name().toString());
        directiveType.setName(toDirectiveName(classInfo, annotations));
        directiveType.setDescription(DescriptionHelper.getDescriptionForType(annotations).orElse(null));
        directiveType.setLocations(getLocations(classInfo.declaredAnnotation(DIRECTIVE)));
        directiveType.setRepeatable(classInfo.hasAnnotation(Annotations.REPEATABLE));

        Class<?> clazz;
        try {
            clazz = Class.forName(directiveType.getClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class for directive: " + directiveType.getClassName(), e);
        }

        for (MethodInfo method : classInfo.methods()) {
            DirectiveArgument argument = new DirectiveArgument();
            if (RequiresScopes.class.isAssignableFrom(clazz) || Policy.class.isAssignableFrom(clazz)) {
                DotName stringDotName = DotName.createSimple(String.class.getName());

                // AnnotationInstance nonNullAnnotation = AnnotationInstance.create(Annotations.NON_NULL, null, Collections.emptyList());

                Type stringType = ClassType.create(stringDotName, Type.Kind.CLASS);
                Type stringArrayType = ArrayType.create(stringType, 1);
                Type string2DArrayType = ArrayType.create(stringArrayType, 1);
                argument.setReference(referenceCreator.createReferenceForOperationArgument(string2DArrayType, null));

                argument.setName(method.name());
                Annotations annotationsForMethod = Annotations.getAnnotationsForInterfaceField(method);
                populateField(Direction.IN, argument, string2DArrayType, annotationsForMethod);
                if (annotationsForMethod.containsOneOfTheseAnnotations(NON_NULL)) {
                    argument.setNotNull(true);
                }
            } else {
                argument.setReference(referenceCreator.createReferenceForOperationArgument(method.returnType(), null));
                argument.setName(method.name());
                Annotations annotationsForMethod = Annotations.getAnnotationsForInterfaceField(method);
                populateField(Direction.IN, argument, method.returnType(), annotationsForMethod);
                if (annotationsForMethod.containsOneOfTheseAnnotations(NON_NULL)) {
                    argument.setNotNull(true);
                }
            }
            directiveType.addArgumentType(argument);
        }

        return directiveType;
    }

    private String toDirectiveName(ClassInfo classInfo, Annotations annotations) {
        String name = TypeNameHelper.getAnyTypeName(classInfo, annotations, getTypeAutoNameStrategy());
        if (Character.isUpperCase(name.charAt(0)))
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return name;
    }

    private Set<String> getLocations(AnnotationInstance directiveAnnotation) {
        return Stream.of(directiveAnnotation.value("on").asEnumArray())
                .collect(toSet());
    }
}

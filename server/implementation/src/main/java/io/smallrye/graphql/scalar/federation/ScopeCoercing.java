package io.smallrye.graphql.scalar.federation;

import static io.smallrye.graphql.SmallRyeGraphQLServerMessages.msg;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import io.smallrye.graphql.api.federation.requiresscopes.Scope;

public class ScopeCoercing implements Coercing<Object, String> {
    private static final DotName Scope = DotName.createSimple(Scope.class.getName());

    private String convertImpl(Object input) {
        if (input instanceof AnnotationInstance) {
            AnnotationInstance annotationInstance = (AnnotationInstance) input;
            if (Scope.equals(annotationInstance.name())) {
                return (String) annotationInstance.value("value").value();
            } else {
                throw new RuntimeException("Can not parse annotation " + annotationInstance.name() + " to Scope");
            }
        } else {
            throw new RuntimeException("Can not parse a Scope from [" + input.toString() + "]");
        }
    }

    @Override
    public String serialize(Object input) {
        if (input == null)
            return null;
        try {
            return convertImpl(input);
        } catch (RuntimeException e) {
            throw msg.coercingSerializeException(Scope.class.getSimpleName(), input.getClass().getSimpleName(),
                    null);
        }
    }

    @Override
    public Object parseValue(Object input) {
        try {
            return convertImpl(input);
        } catch (RuntimeException e) {
            throw msg.coercingParseValueException(Scope.class.getSimpleName(), input.getClass().getSimpleName(), e);
        }
    }

    @Override
    public Object parseLiteral(Object input) {
        if (input == null)
            return null;

        if (input instanceof StringValue) {
            return ((StringValue) input).getValue();
        } else {
            throw msg.coercingParseLiteralException(input.getClass().getSimpleName());
        }
    }

    @Override
    public Value<?> valueToLiteral(Object input) {
        String s = serialize(input);
        return StringValue.newStringValue(s).build();
    }

}

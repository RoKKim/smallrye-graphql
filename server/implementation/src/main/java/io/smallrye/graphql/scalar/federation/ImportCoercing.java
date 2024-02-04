package io.smallrye.graphql.scalar.federation;

import static io.smallrye.graphql.SmallRyeGraphQLServerMessages.msg;

import java.util.HashMap;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import io.smallrye.graphql.api.federation.link.Import;

public class ImportCoercing implements Coercing<Object, Object> {
    private static final DotName IMPORT = DotName.createSimple(Import.class.getName());

    private Object convertImpl(Object input) {
        if (input instanceof AnnotationInstance) {
            AnnotationInstance annotationInstance = (AnnotationInstance) input;
            if (IMPORT.equals(annotationInstance.name())) {
                String name = (String) annotationInstance.value("name").value();
                String as;
                if (annotationInstance.value("as") != null) {
                    as = (String) annotationInstance.value("as").value();
                    if (name.equals(as)) {
                        return name;
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("name", name);
                    result.put("as", as);
                    return result;
                }
                return name;
            } else {
                throw new RuntimeException("Can not parse annotation " + annotationInstance.name() + " to Import");
            }
        } else {
            throw new RuntimeException("Can not parse a Import from [" + input.toString() + "]");
        }
    }

    @Override
    public Object serialize(Object input) {
        if (input == null)
            return null;
        try {
            return convertImpl(input);
        } catch (RuntimeException e) {
            throw msg.coercingSerializeException(Import.class.getSimpleName(), input.getClass().getSimpleName(),
                    null);
        }
    }

    @Override
    public Object parseValue(Object input) {
        try {
            return convertImpl(input);
        } catch (RuntimeException e) {
            throw msg.coercingParseValueException(Import.class.getSimpleName(), input.getClass().getSimpleName(), e);
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
        Object s = serialize(input);
        if (s instanceof String) {
            return StringValue.newStringValue((String) s).build();
        } else {
            Map<String, Object> map = (Map<String, Object>) s;
            StringValue name = StringValue.newStringValue((String) map.get("name")).build();
            StringValue as = StringValue.newStringValue((String) map.get("as")).build();
            return ObjectValue.newObjectValue()
                    .objectField(new ObjectField("name", name))
                    .objectField(new ObjectField("as", as))
                    .build();
        }
    }

}

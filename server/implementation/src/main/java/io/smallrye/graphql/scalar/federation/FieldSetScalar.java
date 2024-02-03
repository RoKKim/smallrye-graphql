package io.smallrye.graphql.scalar.federation;

import io.smallrye.graphql.api.federation.FieldSet;
import io.smallrye.graphql.scalar.AbstractScalar;

/**
 * Scalar for FieldSet.
 * Based on graphql-java's Scalars.GraphQLString
 */
public class FieldSetScalar extends AbstractScalar {

    public FieldSetScalar() {

        super("FieldSet", new FieldSetCoercing(), FieldSet.class);
    }
}

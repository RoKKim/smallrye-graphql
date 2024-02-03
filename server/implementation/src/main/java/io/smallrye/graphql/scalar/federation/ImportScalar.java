package io.smallrye.graphql.scalar.federation;

import graphql.Scalars;
import io.smallrye.graphql.scalar.AbstractScalar;

/**
 * Scalar for Import.
 * Based on graphql-java's Scalars.GraphQLString
 */
public class ImportScalar extends AbstractScalar {

    public ImportScalar() {
        // todo RokM coercing
        super("Import", Scalars.GraphQLString.getCoercing(), String.class);
    }
}

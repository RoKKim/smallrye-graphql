package io.smallrye.graphql.scalar.federation;

import io.smallrye.graphql.api.federation.requiresscopes.Scope;
import io.smallrye.graphql.scalar.AbstractScalar;

/**
 * Scalar for {@link Scope}.
 */
public class ScopeScalar extends AbstractScalar {

    public ScopeScalar() {

        super("Scope", new ScopeCoercing(), Scope.class);
    }
}

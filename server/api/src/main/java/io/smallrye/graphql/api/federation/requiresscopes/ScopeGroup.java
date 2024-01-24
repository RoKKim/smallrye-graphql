package io.smallrye.graphql.api.federation.requiresscopes;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import org.eclipse.microprofile.graphql.NonNull;

/**
 * @author RokM
 */
@Retention(RUNTIME)
// todo RokM documentation
public @interface ScopeGroup {
    @NonNull
    String[] value();
}

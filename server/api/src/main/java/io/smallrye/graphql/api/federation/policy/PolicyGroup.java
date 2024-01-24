package io.smallrye.graphql.api.federation.policy;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import org.eclipse.microprofile.graphql.NonNull;

/**
 * @author RokM
 */
@Retention(RUNTIME)
// todo RokM documentation
public @interface PolicyGroup {
    @NonNull
    String[] value();
}

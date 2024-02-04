package io.smallrye.graphql.api.federation.link;

import org.eclipse.microprofile.graphql.Ignore;

/**
 * The role of a {@link Link} schema. This behavior is different for {@link Link} with a specified purpose:
 * <ul>
 * <li><b>SECURITY</b> links convey metadata necessary to compute the API schema and securely resolve fields within it</li>
 * <li><b>EXECUTION</b> links convey metadata necessary to correctly resolve fields within the schema</li>
 * <li><b>UNDEFINED</b> is used for internal purposes as the default value that should be ignored and not used in the
 * schema</li>
 * </ul>
 */
public enum Purpose {
    SECURITY,
    EXECUTION,
    @Ignore
    UNDEFINED
}

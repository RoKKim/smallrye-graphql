package io.smallrye.graphql.api.federation.link;

/**
 * The role of a {@link Link} schema. This behavior is different for {@link Link} with a specified purpose:
 * <ul>
 * <li><b>SECURITY</b> links convey metadata necessary to compute the API schema and securely resolve fields within it</li>
 * <li><b>EXECUTION</b> links convey metadata necessary to correctly resolve fields within the schema</li>
 * </ul>
 */
public enum Purpose {
    SECURITY,
    EXECUTION
}

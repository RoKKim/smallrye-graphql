package io.smallrye.graphql.api.federation.link;

import static io.smallrye.graphql.api.DirectiveLocation.SCHEMA;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;

import io.smallrye.common.annotation.Experimental;
import io.smallrye.graphql.api.Directive;
import io.smallrye.graphql.api.federation.link.Link.Links;

/**
 * <b><code>directive @link(url: String!, as: String, import: [Import], for: Purpose) repeatable on SCHEMA</code></b>
 *
 * @see <a href="https://www.apollographql.com/docs/federation/federated-types/federated-directives/#importing-directives">
 *      federation spec</a>
 */
@Directive(on = { SCHEMA })
@Description("This directive links definitions from an external specification to this schema.")
@Retention(RUNTIME)
@Repeatable(Links.class)
@Experimental("SmallRye GraphQL Federation is still subject to change.")
public @interface Link {
    public static final String FEDERATION_SPEC_URL = "https://specs.apollo.dev/federation";
    // todo RokM raise to 2.6
    public static final String FEDERATION_SPEC_LATEST_VER = "2.5";
    public static final String FEDERATION_SPEC_LATEST_URL = FEDERATION_SPEC_URL + "/v" + FEDERATION_SPEC_LATEST_VER;

    @NonNull
    @Description("The foreign schema’s URL.")
    String url();

    // todo RokM check default
    @Description("Change the namespace prefix assigned to the foreign schema.")
    String as() default "";

    // todo RokM check default
    // todo RokM change to type LinkImport
    @Description("A list of elements to import from the foreign schema into the document.")
    @Name("import")
    String[] imports() default {};

    // todo RokM add
    //    @Description("An optional purpose for this link. This hints to consumers as to whether they can safely ignore " +
    //            "metadata described by a foreign schema.")
    //    @Name("for")
    //    Purpose forPurpose();

    @Retention(RUNTIME)
    @interface Links {
        Link[] value();
    }
}

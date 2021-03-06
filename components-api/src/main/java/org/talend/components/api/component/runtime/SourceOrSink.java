package org.talend.components.api.component.runtime;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.avro.Schema;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.daikon.NamedThing;
import org.talend.daikon.properties.ValidationResult;

public interface SourceOrSink extends Serializable {

    /**
     * Initialize based on the specified properties.
     */
    public void initialize(RuntimeContainer container, ComponentProperties properties);

    /**
     * Checks that this source or sink is valid, before it can be used in a pipeline.
     */
    public ValidationResult validate(RuntimeContainer container);

    /**
     * Get the list of schema names available for this {@code Source} or an empty List if none.
     */
    public List<NamedThing> getSchemaNames(RuntimeContainer adaptor) throws IOException;

    /**
     * Return the schema associated with the specified schema name for this {@code Source}, or null if none.
     */
    public Schema getSchema(RuntimeContainer container, String schemaName) throws IOException;

}

// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.common;

import java.util.HashSet;
import java.util.Set;

import org.apache.avro.Schema;
import org.talend.components.api.component.Connector;
import org.talend.components.api.component.PropertyPathConnector;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.daikon.NamedThing;
import org.talend.daikon.properties.Property;
import org.talend.daikon.properties.Property.Type;
import org.talend.daikon.properties.PropertyFactory;

/**
 * this class provide a simple mechanism for handle Properties component with a fixed set of connection (hence a fixed
 * set of schemas )in {@link Property}. This is supposed to be used a direct ComponentProperties and not nested ones.
 */
public abstract class FixedConnectorsComponentProperties extends ComponentProperties {

    /**
     * FixedSchemaComponentProperties constructor comment.
     * 
     * @param name
     */
    public FixedConnectorsComponentProperties(String name) {
        super(name);
    }

    /**
     * This default implementation uses {@link PropertyPathConnector} to find the SchemaProperties or Property of type
     * Schema instances avaialble in this Object. It return null if none found
     */
    @Override
    public Schema getSchema(Connector connector, boolean isOutputConnection) {
        if (connector instanceof PropertyPathConnector) {
            NamedThing property = getProperty(((PropertyPathConnector) connector).getPropertyPath());
            if (property != null) {
                Property schemaProp = null;
                if (property instanceof SchemaProperties) {
                    SchemaProperties schemaProperties = (SchemaProperties) property;
                    schemaProp = schemaProperties.schema;
                } else if (property instanceof Property) {
                    schemaProp = (Property) property;
                }
                return (schemaProp != null && schemaProp.getType() == Type.SCHEMA) ? (Schema) schemaProp.getValue() : null;
            } // else // wrong type or path not found so return null
        } // not a connector handled by this class
        return null;
    }

    /**
     * provide the list of all {@link PropertyPathConnector} related to the supported schemas properties used for input
     * (isOutputConnection=false) or output (isOutputConnection=true). The paths may refer to a Property of type Schema
     * (see {@link Property.Type#SCHEMA} and see {@link PropertyFactory#newSchema(String)}) or a SchemaProperties. The
     * path may be used by {@link #getProperty(String)}.
     */
    abstract protected Set<PropertyPathConnector> getAllSchemaPropertiesConnectors(boolean isOutputConnection);

    /**
     * this implmentation simply compute the diff between all connection names returned by
     * {@link #getAllSchemaPropertiesConnectors(boolean)} and the existingConnectors.
     * 
     * @param existingConnectors list of connectors already connected that may be of use to compute what remains to be
     * connected.
     * @param isOutputConnection wether we query the possible output or input connections.
     * @return set of connector left to be connected.
     */
    @Override
    public Set<Connector> getAvailableConnectors(Set<Connector> existingConnectors, boolean isOutputConnection) {
        return computeDiff(getAllSchemaPropertiesConnectors(isOutputConnection), existingConnectors);
    }

    private Set<Connector> computeDiff(Set<PropertyPathConnector> allConnectors, Set<Connector> existingConnections) {
        Set<Connector> diff = new HashSet<>(allConnectors);
        if (existingConnections != null) {
            diff.removeAll(existingConnections);
        } // else null so nothing to remove
        return diff;
    }

}

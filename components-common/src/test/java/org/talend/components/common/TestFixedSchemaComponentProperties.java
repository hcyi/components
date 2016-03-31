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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.avro.Schema;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.talend.components.api.component.Connector;
import org.talend.components.api.component.Connector.ConnectorType;
import org.talend.components.api.component.PropertyPathConnector;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.api.service.ComponentService;
import org.talend.components.api.service.internal.ComponentServiceImpl;
import org.talend.components.api.test.SimpleComponentDefinition;
import org.talend.components.api.test.SimpleComponentRegistry;
import org.talend.daikon.properties.Property;
import org.talend.daikon.properties.PropertyFactory;

public class TestFixedSchemaComponentProperties {

    static public class TestProperties extends FixedConnectorsComponentProperties {

        static final PropertyPathConnector REJECT_CONNECTOR = new PropertyPathConnector(ConnectorType.REJECT, "reject");

        static final PropertyPathConnector MAIN_CONNECTOR = new PropertyPathConnector(ConnectorType.MAIN, "main");

        public SchemaProperties main = new SchemaProperties("main");

        public Property reject = PropertyFactory.newSchema("reject"); //$NON-NLS-1$

        /**
         * 
         * @param name
         */
        public TestProperties(String name) {
            super(name);
        }

        @Override
        public void setupProperties() {
            super.setupProperties();
            reject.setValue(SchemaProperties.EMPTY_SCHEMA);
        }

        @Override
        protected Set<PropertyPathConnector> getAllSchemaPropertiesConnectors(boolean isOutputConnection) {
            HashSet<PropertyPathConnector> connectorsSet = new HashSet<>();
            connectorsSet.add(MAIN_CONNECTOR);
            connectorsSet.add(REJECT_CONNECTOR);
            return connectorsSet;
        }

    }

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    // default implementation for pure java test. Shall be overriden of Spring or OSGI tests
    public ComponentService getComponentService() {
        SimpleComponentRegistry testComponentRegistry = new SimpleComponentRegistry();
        SimpleComponentDefinition componentDef = new SimpleComponentDefinition();
        componentDef.setPropertyClass(TestProperties.class);
        testComponentRegistry.addComponent("name", componentDef);
        return new ComponentServiceImpl(testComponentRegistry);
    }

    /**
     * Test method for
     * {@link org.talend.components.common.FixedConnectorsComponentProperties#getSchema(java.lang.String, boolean)}.
     */
    @Test
    public void testGetSchemaStringBoolean() {
        TestProperties properties = (TestProperties) getComponentService().getComponentProperties("name"); //$NON-NLS-1$
        Schema schema = properties.getSchema(TestProperties.MAIN_CONNECTOR, true);
        assertNotNull(schema);
        schema = properties.getSchema(TestProperties.REJECT_CONNECTOR, true);
        assertNotNull(schema);
        schema = properties.getSchema(new PropertyPathConnector(ConnectorType.MAIN, "foo"), true); //$NON-NLS-1$
        assertNull(schema);
    }

    @Test
    public void checkFixedSchemaPathProperties() {
        CommonTestUtils.checkAllSchemaPathAreSchemaTypes(getComponentService(), errorCollector);
    }

    @Test
    public void testGetAvailableconnectors() {
        ComponentProperties componentProperties = getComponentService().getComponentProperties("name"); //$NON-NLS-1$
        Set<Connector> availableConnections = componentProperties.getAvailableConnectors(null, true);
        assertThat(availableConnections, hasSize(2));
        availableConnections = componentProperties.getAvailableConnectors(Collections.singleton(TestProperties.MAIN_CONNECTOR),
                true);
        assertThat(availableConnections, hasSize(1));
        assertThat(availableConnections, contains(TestProperties.REJECT_CONNECTOR));
    }

}

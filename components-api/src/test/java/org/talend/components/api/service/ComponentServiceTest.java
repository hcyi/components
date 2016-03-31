// ============================================================================
//
// Copyright (C) 2006-2015 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.api.service;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.talend.components.api.component.ComponentDefinition;
import org.talend.components.api.component.Connector;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.api.service.internal.ComponentServiceImpl;
import org.talend.components.api.service.testcomponent.ComponentPropertiesWithDefinedI18N;
import org.talend.components.api.service.testcomponent.TestComponentDefinition;
import org.talend.components.api.service.testcomponent.TestComponentProperties;
import org.talend.components.api.service.testcomponent.TestComponentWizard;
import org.talend.components.api.service.testcomponent.TestComponentWizardDefinition;
import org.talend.components.api.service.testcomponent.nestedprop.NestedComponentProperties;
import org.talend.components.api.test.ComponentTestUtils;
import org.talend.components.api.test.SimpleComponentRegistry;
import org.talend.components.api.wizard.ComponentWizard;
import org.talend.components.api.wizard.WizardImageType;

public class ComponentServiceTest extends AbstractComponentTest {

    static class NotExistingComponentProperties extends ComponentProperties {

        /**
         * DOC sgandon NotExistingComponentProperties constructor comment.
         * 
         * @param name
         */
        public NotExistingComponentProperties() {
            super("foo");
        }
    }

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    private ComponentServiceImpl componentService;

    @Before
    public void initializeComponentRegistryAnsService() {
        // reset the component service
        componentService = null;
    }

    // default implementation for pure java test. Shall be overriden of Spring or OSGI tests
    @Override
    public ComponentService getComponentService() {
        if (componentService == null) {
            SimpleComponentRegistry testComponentRegistry = new SimpleComponentRegistry();
            testComponentRegistry.addComponent(TestComponentDefinition.COMPONENT_NAME, new TestComponentDefinition());
            testComponentRegistry.addWizard(TestComponentWizardDefinition.COMPONENT_WIZARD_NAME,
                    new TestComponentWizardDefinition());
            componentService = new ComponentServiceImpl(testComponentRegistry);
        }
        return componentService;
    }

    @Test
    public void testSupportsProps() throws Throwable {
        ComponentProperties props = getComponentService().getComponentProperties(TestComponentDefinition.COMPONENT_NAME);
        ComponentPropertiesWithDefinedI18N anotherProp = (ComponentPropertiesWithDefinedI18N) new ComponentPropertiesWithDefinedI18N(
                "foo").init();
        List<ComponentDefinition> comps = getComponentService().getPossibleComponents(props, anotherProp);
        assertEquals("TestComponent", comps.get(0).getName());

        comps = getComponentService().getPossibleComponents(new NestedComponentProperties("props"),
                new NotExistingComponentProperties());
        assertEquals(0, comps.size());
    }

    @Test
    public void testGetWizardIconOk() {
        InputStream iconStream = getComponentService().getWizardPngImage(TestComponentWizardDefinition.COMPONENT_WIZARD_NAME,
                WizardImageType.TREE_ICON_16X16);
        assertNotNull(iconStream);
    }

    @Test(expected = ComponentException.class)
    public void testGetWizardIconWrongName() {
        InputStream iconStream = getComponentService().getWizardPngImage("not an existing wizard name",
                WizardImageType.TREE_ICON_16X16);
        assertNull(iconStream);
    }

    @Test
    public void testGetWizard() {
        ComponentWizard wizard = getComponentService().getComponentWizard(TestComponentWizardDefinition.COMPONENT_WIZARD_NAME,
                "userdata");
        assertTrue(wizard instanceof TestComponentWizard);
        assertEquals("userdata", wizard.getRepositoryLocation());
    }

    @Test(expected = ComponentException.class)
    public void testGetWizardNotFound() {
        getComponentService().getComponentWizard("not found", "userdata");
    }

    @Test
    public void testGetWizardWithProps() {
        TestComponentWizard wizard = (TestComponentWizard) getComponentService()
                .getComponentWizard(TestComponentWizardDefinition.COMPONENT_WIZARD_NAME, "userdata");
        wizard.props = new TestComponentProperties("props").init();
        ComponentProperties props = (ComponentProperties) wizard.props;
        List<ComponentWizard> wizards = getComponentService().getComponentWizardsForProperties(props, "userdata");
        assertTrue(props == ((TestComponentWizard) wizards.get(0)).props);
    }

    @Test
    public void testFamilies() {
        TestComponentDefinition testComponentDefinition = new TestComponentDefinition();
        assertEquals(2, testComponentDefinition.getFamilies().length);
    }

    @Test
    public void testAlli18n() {
        ComponentTestUtils.testAlli18n(getComponentService(), errorCollector);
    }

    @Test
    public void testAllImages() {
        ComponentTestUtils.testAllImages(getComponentService());
    }

    @Test
    public void testAllRuntime() {
        ComponentTestUtils.testAllRuntimeAvaialble(getComponentService());
    }

    @Test
    public void testGetDependencies() {
        // check the comp def return the proper stream for the pom
        Set<String> mavenUriDependencies = getComponentService().getMavenUriDependencies(TestComponentDefinition.COMPONENT_NAME);
        assertEquals(5, mavenUriDependencies.size());
        assertThat(mavenUriDependencies,
                containsInAnyOrder("mvn:org.apache.maven/maven-core/3.3.3/jar", //
                        "mvn:org.eclipse.sisu/org.eclipse.sisu.plexus/0.0.0.M2a/jar", //
                        "mvn:org.apache.maven/maven-artifact/3.3.3/jar", //
                        "mvn:org.eclipse.aether/aether-transport-file/1.0.0.v20140518/jar", //
                        "mvn:org.talend.components/file-input/0.1.0.SNAPSHOT/jar"//
        ));
    }

    @Test
    public void testGetAllDepenendencies() {
        ComponentTestUtils.testAllDesignDependenciesPresent(getComponentService(), errorCollector);
    }

    @Test
    public void testGetSchema() {
        TestComponentProperties componentProperties = (TestComponentProperties) getComponentService()
                .getComponentProperties(TestComponentDefinition.COMPONENT_NAME);
        Schema aSchema = SchemaBuilder.builder().booleanType();
        componentProperties.mainOutput.setValue(aSchema);
        Schema schema = getComponentService().getSchema(componentProperties,
                componentProperties.getAllConnectors().iterator().next(), true);
        assertEquals(aSchema, schema);
        schema = getComponentService().getSchema(componentProperties, new Connector() {

            @Override
            public ConnectorType getType() {
                return null;
            }
        }, true);
        assertNull(schema);
    }

    @Test
    public void testAvailalbleConnectors() {
        TestComponentProperties componentProperties = (TestComponentProperties) getComponentService()
                .getComponentProperties(TestComponentDefinition.COMPONENT_NAME);
        Set<Connector> availableConnectors = getComponentService().getAvailableConnectors(componentProperties,
                Collections.EMPTY_SET, true);
        assertThat(availableConnectors, not(is(empty())));
        Connector mainConnector = componentProperties.getAllConnectors().iterator().next();
        assertEquals(availableConnectors.iterator().next(), mainConnector);
        availableConnectors = getComponentService().getAvailableConnectors(componentProperties,
                Collections.singleton(mainConnector), true);
        assertThat(availableConnectors, is(empty()));
    }

}

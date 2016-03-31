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
package org.talend.components.salesforce.tsalesforcebulkexec;

import aQute.bnd.annotation.component.Component;
import org.talend.components.api.Constants;
import org.talend.components.api.component.ComponentDefinition;
import org.talend.components.api.component.InputComponentDefinition;
import org.talend.components.api.component.Trigger;
import org.talend.components.api.component.Trigger.TriggerType;
import org.talend.components.api.component.runtime.Source;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.salesforce.SalesforceDefinition;
import org.talend.components.salesforce.SalesforceModuleProperties;
import org.talend.components.salesforce.runtime.SalesforceSource;

@Component(name = Constants.COMPONENT_BEAN_PREFIX
        + TSalesforceBulkExecDefinition.COMPONENT_NAME, provide = ComponentDefinition.class)
public class TSalesforceBulkExecDefinition extends SalesforceDefinition implements InputComponentDefinition {

    public static final String COMPONENT_NAME = "tSalesforceBulkExecNew"; //$NON-NLS-1$

    public TSalesforceBulkExecDefinition() {
        super(COMPONENT_NAME);
        // no input connector this is an starting component
        setTriggers(new Trigger(TriggerType.SUBJOB_OK, 1, 0), new Trigger(TriggerType.SUBJOB_ERROR, 1, 0));
    }

    @Override
    public boolean isConditionalInputs() {
        return true;
    }

    @Override
    public Class<? extends ComponentProperties> getPropertyClass() {
        return TSalesforceBulkExecProperties.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends ComponentProperties>[] getNestedCompatibleComponentPropertiesClass() {
        return concatPropertiesClasses(super.getNestedCompatibleComponentPropertiesClass(),
                new Class[] { SalesforceModuleProperties.class });
    }

    @Override
    public Source getRuntime() {
        return new SalesforceSource();
    }

}

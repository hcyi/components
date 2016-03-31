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
package org.talend.components.salesforce.runtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.salesforce.tsalesforceinput.TSalesforceInputProperties;

import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.bind.XmlObject;

public class SalesforceInputReader extends SalesforceReader<IndexedRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceInputReader.class);

    private transient QueryResult inputResult;

    private transient SObject[] inputRecords;

    private transient int inputRecordsIndex;

    public SalesforceInputReader(RuntimeContainer container, SalesforceSource source, TSalesforceInputProperties props) {
        super(container, source);
        properties = props;
    }

    @Override
    protected Schema getSchema() throws IOException {
        TSalesforceInputProperties inProperties = (TSalesforceInputProperties) properties;
        if (querySchema == null) {
            querySchema = super.getSchema();
            if (inProperties.manualQuery.getBooleanValue()) {
                SObject currentSObject = getCurrentSObject();
                Iterator<XmlObject> children = currentSObject.getChildren();
                List<String> columnsName = new ArrayList<>();
                while (children.hasNext()) {
                    columnsName.add(children.next().getName().getLocalPart());
                }

                List<Schema.Field> copyFieldList = new ArrayList<>();
                for (Schema.Field se : querySchema.getFields()) {
                    if (columnsName.contains(se.name())) {
                        copyFieldList.add(new Schema.Field(se.name(), se.schema(), se.doc(), se.defaultVal()));
                    }
                }
                Map<String, Object> objectProps = querySchema.getObjectProps();
                querySchema = Schema.createRecord(querySchema.getName(), querySchema.getDoc(), querySchema.getNamespace(),
                        querySchema.isError());
                querySchema.getObjectProps().putAll(objectProps);
                querySchema.setFields(copyFieldList);
            }
        }
        return querySchema;
    }

    @Override
    public boolean start() throws IOException {
        try {
            inputResult = executeSalesforceQuery();
            if (inputResult.getSize() == 0) {
                return false;
            }
            inputRecords = inputResult.getRecords();
            inputRecordsIndex = 0;
            return inputRecords.length > 0;
        } catch (ConnectionException e) {
            // Wrap the exception in an IOException.
            throw new IOException(e);
        }
    }

    @Override
    public boolean advance() throws IOException {
        inputRecordsIndex++;

        // Fast return conditions.
        if (inputRecordsIndex < inputRecords.length) {
            return true;
        }
        if (inputResult.isDone()) {
            return false;
        }

        try {
            inputResult = getConnection().queryMore(inputResult.getQueryLocator());
            inputRecords = inputResult.getRecords();
            inputRecordsIndex = 0;
            return inputResult.getSize() > 0;
        } catch (ConnectionException e) {
            // Wrap the exception in an IOException.
            throw new IOException(e);
        }

    }

    public SObject getCurrentSObject() throws NoSuchElementException {
        return inputRecords[inputRecordsIndex];
    }

    protected QueryResult executeSalesforceQuery() throws IOException, ConnectionException {
        TSalesforceInputProperties inProperties = (TSalesforceInputProperties) properties;
        getConnection().setQueryOptions(inProperties.batchSize.getIntValue());
        return getConnection().query(getQueryString(inProperties));
    }

    @Override
    public IndexedRecord getCurrent() {
        try {
            return ((SObjectAdapterFactory) getFactory()).convertToAvro(getCurrentSObject());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

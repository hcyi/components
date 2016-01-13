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
package org.talend.components.mongodb.tmongodbinputv2;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.facet.SimpleInputFacetV2;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

public class MongoDBInputFacet extends SimpleInputFacetV2<DBObject> {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBInputFacet.class);

    public MongoDBInputFacet() {
        // TODO get properties
    }

    private MongoClient mongo = null;

    private DB db = null;

    @Override
    public void setUp(DoFn<Void, DBObject>.Context context) {
        MongoClientOptions clientOptions = new MongoClientOptions.Builder().build();
        List<MongoCredential> mongoCredentialList = new ArrayList<MongoCredential>();
        ServerAddress serverAddress = new ServerAddress("127.0.0.1", 27017);
        mongo = new MongoClient(serverAddress, mongoCredentialList, clientOptions);
        this.db = mongo.getDB("test");
    }

    @Override
    public void execute() throws Exception {
        DBCollection coll = db.getCollection("testCollection");
        com.mongodb.DBObject myQuery = (com.mongodb.DBObject) com.mongodb.util.JSON.parse("{}");
        com.mongodb.DBObject fields = new com.mongodb.BasicDBObject();
        DBCursor cursor = coll.find(myQuery, fields);
        while (cursor.hasNext()) {
            this.addToMainOutput(cursor.next());
        }
    }

    @Override
    public void tearDown(DoFn<Void, DBObject>.Context context) {
        if (mongo != null) {
            mongo.close();
        }
    }
}
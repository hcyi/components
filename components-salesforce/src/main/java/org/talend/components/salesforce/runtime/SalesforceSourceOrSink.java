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
package org.talend.components.salesforce.runtime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.avro.Schema;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.component.runtime.SourceOrSink;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.common.ProxyProperties;
import org.talend.components.common.runtime.ProxyPropertiesRuntimeHelper;
import org.talend.components.salesforce.SalesforceConnectionProperties;
import org.talend.components.salesforce.SalesforceProvideConnectionProperties;
import org.talend.components.salesforce.connection.oauth.SalesforceOAuthConnection;
import org.talend.daikon.NamedThing;
import org.talend.daikon.SimpleNamedThing;
import org.talend.daikon.properties.ValidationResult;

import com.sforce.async.AsyncApiException;
import com.sforce.async.BulkConnection;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import com.sforce.soap.partner.DescribeSObjectResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.SessionHeader_element;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.SessionRenewer;

public class SalesforceSourceOrSink implements SourceOrSink {

    private transient static final Logger LOG = LoggerFactory.getLogger(SalesforceSourceOrSink.class);

    protected static final String API_VERSION = "34.0";

    protected SalesforceProvideConnectionProperties properties;

    protected static final String KEY_CONNECTION = "Connection";

    @Override
    public void initialize(RuntimeContainer container, ComponentProperties properties) {
        this.properties = (SalesforceProvideConnectionProperties) properties;
    }

    @Override
    public ValidationResult validate(RuntimeContainer container) {
        ValidationResult vr = new ValidationResult();
        try {
            connect(container);
        } catch (IOException ex) {
            return exceptionToValidationResult(ex);
        }
        return vr;
    }

    protected static ValidationResult exceptionToValidationResult(Exception ex) {
        ValidationResult vr = new ValidationResult();
        // FIXME - do a better job here
        vr.setMessage(ex.getMessage());
        vr.setStatus(ValidationResult.Result.ERROR);
        return vr;
    }

    public static ValidationResult validateConnection(SalesforceProvideConnectionProperties properties) {
        SalesforceSourceOrSink ss = new SalesforceSourceOrSink();
        ss.initialize(null, (ComponentProperties) properties);
        return ss.validate(null);
    }

    public SalesforceConnectionProperties getConnectionProperties() {
        return properties.getConnectionProperties();
    }

    protected BulkConnection connectBulk(ConnectorConfig config) throws ComponentException {
        final SalesforceConnectionProperties connProps = properties.getConnectionProperties();
        /*
         * When PartnerConnection is instantiated, a login is implicitly executed and, if successful, a valid session is
         * stored in the ConnectorConfig instance. Use this key to initialize a BulkConnection:
         */
        ConnectorConfig bulkConfig = new ConnectorConfig();
        bulkConfig.setSessionId(config.getSessionId());
        /*
         * The endpoint for the Bulk API service is the same as for the normal SOAP uri until the /Soap/ part. From here
         * it's '/async/versionNumber'
         */
        String soapEndpoint = config.getServiceEndpoint();
        String restEndpoint = soapEndpoint.substring(0, soapEndpoint.indexOf("Soap/")) + "async/" + API_VERSION;
        bulkConfig.setRestEndpoint(restEndpoint);
        // This should only be false when doing debugging.
        bulkConfig.setCompression(connProps.needCompression.getBooleanValue());
        bulkConfig.setTraceMessage(connProps.httpTraceMessage.getBooleanValue());

        try {
            return new BulkConnection(bulkConfig);
        } catch (AsyncApiException e) {
            throw new ComponentException(e);
        }
    }

    protected PartnerConnection doConnection(ConnectorConfig config) throws ConnectionException {
        SalesforceConnectionProperties connProps = properties.getConnectionProperties();
        if (SalesforceConnectionProperties.LOGIN_OAUTH.equals(connProps.loginType.getValue())) {
            SalesforceOAuthConnection oauthConnection = new SalesforceOAuthConnection(connProps.oauth,
                    SalesforceConnectionProperties.OAUTH_URL, API_VERSION);
            oauthConnection.login(config);
        } else {
            config.setAuthEndpoint(SalesforceConnectionProperties.URL);
        }
        PartnerConnection connection;
        connection = new PartnerConnection(config);
        return connection;
    }

    class ConnectionHolder {

        PartnerConnection connection;

        BulkConnection bulkConnection;
    }

    protected ConnectionHolder connect(RuntimeContainer container) throws IOException {

        final ConnectionHolder ch = new ConnectionHolder();
        SalesforceConnectionProperties connProps = properties.getConnectionProperties();
        String refComponentId = connProps.getReferencedComponentId();
        // Using another component's connection
        if (refComponentId != null) {
            // In a runtime container
            if (container != null) {
                PartnerConnection conn = (PartnerConnection) container.getComponentData(refComponentId, KEY_CONNECTION);
                if (conn != null) {
                    ch.connection = conn;
                    return ch;
                }
                throw new IOException("Referenced component: " + refComponentId + " not connected");
            }
            // Design time
            connProps = connProps.getReferencedConnectionProperties();
        }

        // FIXME add back reffed connection

        ConnectorConfig config = new ConnectorConfig();
        config.setUsername(StringUtils.strip(connProps.userPassword.userId.getStringValue(), "\""));
        config.setPassword(StringUtils.strip(connProps.userPassword.password.getStringValue(), "\"")
                + StringUtils.strip(connProps.userPassword.securityKey.getStringValue(), "\""));

        ProxyPropertiesRuntimeHelper.setProxy(connProps.proxy, ProxyProperties.ProxyType.SOCKS);

        setProxy(config);

        // Notes on how to test this
        // http://thysmichels.com/2014/02/15/salesforce-wsc-partner-connection-session-renew-when-session-timeout/

        config.setSessionRenewer(new SessionRenewer() {

            @Override
            public SessionRenewalHeader renewSession(ConnectorConfig connectorConfig) throws ConnectionException {
                SessionRenewalHeader header = new SessionRenewalHeader();
                // FIXME - session id need to be null for trigger the login?
                // connectorConfig.setSessionId(null);
                doConnection(connectorConfig);

                SessionHeader_element h = ch.connection.getSessionHeader();
                // FIXME - one or the other, I have seen both
                // header.name = new QName("urn:partner.soap.sforce.com", "X-SFDC-Session");
                header.name = new QName("urn:partner.soap.sforce.com", "SessionHeader");
                header.headerElement = h.getSessionId();
                return header;
            }
        });

        if (connProps.timeout.getIntValue() > 0) {
            config.setConnectionTimeout(connProps.timeout.getIntValue());
        }
        config.setCompression(connProps.needCompression.getBooleanValue());
        if (false) {
            config.setTraceMessage(true);
        }
        config.setUseChunkedPost(connProps.httpChunked.getBooleanValue());

        try {
            ch.connection = doConnection(config);
            if (ch.connection != null) {
                String clientId = connProps.clientId.getStringValue();
                if (clientId != null) {
                    // Need the test.
                    ch.connection.setCallOptions(clientId, null);
                }
            }
            if (connProps.bulkConnection.getBooleanValue()) {
                ch.bulkConnection = connectBulk(ch.connection.getConfig());
            }
            if (container != null) {
                container.setComponentData(container.getCurrentComponentId(), KEY_CONNECTION, ch.connection);
            }
            return ch;
        } catch (ConnectionException e) {
            throw new IOException(e);
        }
    }

    public static List<NamedThing> getSchemaNames(RuntimeContainer container, SalesforceProvideConnectionProperties properties)
            throws IOException {
        SalesforceSourceOrSink ss = new SalesforceSourceOrSink();
        ss.initialize(null, (ComponentProperties) properties);
        try {
            PartnerConnection connection = ss.connect(container).connection;
            return ss.getSchemaNames(container);
        } catch (Exception ex) {
            throw new ComponentException(exceptionToValidationResult(ex));
        }
    }

    @Override
    public List<NamedThing> getSchemaNames(RuntimeContainer container) throws IOException {
        return getSchemaNames(connect(container).connection);
    }

    protected List<NamedThing> getSchemaNames(PartnerConnection connection) throws IOException {
        List<NamedThing> returnList = new ArrayList<>();
        DescribeGlobalResult result = null;
        try {
            result = connection.describeGlobal();
        } catch (ConnectionException e) {
            throw new ComponentException(e);
        }
        DescribeGlobalSObjectResult[] objects = result.getSobjects();
        for (DescribeGlobalSObjectResult obj : objects) {
            LOG.debug("module label: " + obj.getLabel() + " name: " + obj.getName());
            returnList.add(new SimpleNamedThing(obj.getName(), obj.getLabel()));
        }
        return returnList;
    }

    public static Schema getSchema(RuntimeContainer container, SalesforceProvideConnectionProperties properties, String module)
            throws IOException {
        SalesforceSourceOrSink ss = new SalesforceSourceOrSink();
        ss.initialize(null, (ComponentProperties) properties);
        PartnerConnection connection = null;
        try {
            connection = ss.connect(container).connection;
        } catch (IOException ex) {
            throw new ComponentException(exceptionToValidationResult(ex));
        }
        return ss.getSchema(connection, module);
    }

    @Override
    public Schema getSchema(RuntimeContainer container, String schemaName) throws IOException {
        return getSchema(connect(container).connection, schemaName);
    }

    protected Schema getSchema(PartnerConnection connection, String module) throws IOException {
        try {
            DescribeSObjectResult[] describeSObjectResults = new DescribeSObjectResult[0];
            describeSObjectResults = connection.describeSObjects(new String[] { module });
            return SalesforceAvroRegistry.get().inferSchema(describeSObjectResults[0]);
        } catch (ConnectionException e) {
            throw new IOException(e);
        }
    }

    private void setProxy(ConnectorConfig config) {
        String proxyHost = null;
        String proxyPort = null;
        String proxyUser = null;
        String proxyPwd = null;
        Proxy.Type proxyType = Proxy.Type.HTTP;
        if (System.getProperty("https.proxyHost") != null) {
            proxyHost = System.getProperty("https.proxyHost");
            proxyPort = System.getProperty("https.proxyPort");
            proxyUser = System.getProperty("https.proxyUser");
            proxyPwd = System.getProperty("https.proxyPassword");
        } else if (System.getProperty("http.proxyHost") != null) {
            proxyHost = System.getProperty("http.proxyHost");
            proxyPort = System.getProperty("http.proxyPort");
            proxyUser = System.getProperty("http.proxyUser");
            proxyPwd = System.getProperty("http.proxyPassword");
        } else if (System.getProperty("socksProxyHost") != null) {
            proxyHost = System.getProperty("socksProxyHost");
            proxyPort = System.getProperty("socksProxyPort");
            proxyUser = System.getProperty("java.net.socks.username");
            proxyPwd = System.getProperty("java.net.socks.password");
            proxyType = Proxy.Type.SOCKS;
        }

        if (proxyHost != null) {
            SocketAddress addr = new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort));
            config.setProxy(new Proxy(proxyType, addr));
            if (proxyUser != null && proxyUser.length() > 0) {
                config.setProxyUsername(proxyUser);
            }
            if (proxyPwd != null && proxyPwd.length() > 0) {
                config.setProxyPassword(proxyPwd);
            }
        } else {
            // No proxy.
        }
    }

    protected void renewSession(ConnectorConfig config) throws ConnectionException {
        SessionRenewer renewer = config.getSessionRenewer();
        renewer.renewSession(config);
    }

}

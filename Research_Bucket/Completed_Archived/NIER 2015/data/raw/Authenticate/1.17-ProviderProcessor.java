/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    private void overrideContentType(PostMethod method) {
        if (endpoint.getOverrideContentTypeHeader() != null) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, "\"" + endpoint.getOverrideContentTypeHeader() + "\"");
        }
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
         // override content-type header
        overrideContentType(method);
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void stop() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        HttpClient client = comp.getClient();
        client.getParams().setSoTimeout(endpoint.getTimeout());
        return client;
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    private void overrideContentType(PostMethod method) {
        if (endpoint.getOverrideContentTypeHeader() != null) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, "\"" + endpoint.getOverrideContentTypeHeader() + "\"");
        }
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
         // override content-type header
        overrideContentType(method);
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void stop() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        HttpClient client = comp.getClient();
        client.getParams().setSoTimeout(endpoint.getTimeout());
        return client;
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void stop() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void stop() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected HttpEndpoint endpoint;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                }
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                        msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    }
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    private HostConfiguration getHostConfiguration(String locationURI) throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient());
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration() {
        HttpComponent comp = (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected HttpEndpoint endpoint;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                }
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                        msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    }
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    private HostConfiguration getHostConfiguration(String locationURI) throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient());
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration() {
        HttpComponent comp = (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.component.ComponentLifeCycle;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.http.HttpLifeCycle;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    protected HttpEndpoint endpoint;
    protected HostConfiguration host;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private String relUri;
    private Map methods;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        java.net.URI uri = java.net.URI.create(endpoint.getLocationURI());
        relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        this.methods = new ConcurrentHashMap();
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }
        PostMethod method = new PostMethod(relUri);
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Uncomment to avoid the http request being sent several times.
            // Can be useful when debugging
            //================================
            //method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            //    public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
            //        return false;
            //    }
            //});
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            int response = getClient().executeMethod(host, method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    public void start() throws Exception {
        URI uri = new URI(endpoint.getLocationURI(), false);
        if (uri.getScheme().equals("https")) {
            ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                            endpoint.getSsl(),
                            endpoint.getKeystoreManager());
            Protocol protocol = new Protocol("https", sf, 443);
            HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            this.host = new HostConfiguration();
            this.host.setHost(host);
        } else {
            this.host = new HostConfiguration();
            this.host.setHost(uri.getHost(), uri.getPort());
        }
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration(HttpEndpoint endpoint) {
        ComponentLifeCycle lf = endpoint.getServiceUnit().getComponent().getLifeCycle();
        return ((HttpLifeCycle) lf).getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        HttpLifeCycle lf = (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        if (lf.getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpLifeCycle lf =  (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        return lf.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.component.ComponentLifeCycle;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.http.HttpLifeCycle;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    protected HttpEndpoint endpoint;
    protected HostConfiguration host;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private String relUri;
    private Map methods;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        java.net.URI uri = java.net.URI.create(endpoint.getLocationURI());
        relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        this.methods = new ConcurrentHashMap();
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }
        PostMethod method = new PostMethod(relUri);
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Uncomment to avoid the http request being sent several times.
            // Can be useful when debugging
            //================================
            //method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            //    public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
            //        return false;
            //    }
            //});
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            int response = getClient().executeMethod(host, method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    public void start() throws Exception {
        URI uri = new URI(endpoint.getLocationURI(), false);
        if (uri.getScheme().equals("https")) {
            ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                            endpoint.getSsl(),
                            endpoint.getKeystoreManager());
            Protocol protocol = new Protocol("https", sf, 443);
            HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            this.host = new HostConfiguration();
            this.host.setHost(host);
        } else {
            this.host = new HostConfiguration();
            this.host.setHost(uri.getHost(), uri.getPort());
        }
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration(HttpEndpoint endpoint) {
        ComponentLifeCycle lf = endpoint.getServiceUnit().getComponent().getLifeCycle();
        return ((HttpLifeCycle) lf).getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        HttpLifeCycle lf = (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        if (lf.getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpLifeCycle lf =  (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        return lf.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    private void overrideContentType(PostMethod method) {
        if (endpoint.getOverrideContentTypeHeader() != null) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, "\"" + endpoint.getOverrideContentTypeHeader() + "\"");
        }
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
         // override content-type header
        overrideContentType(method);
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void stop() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        HttpClient client = comp.getClient();
        client.getParams().setSoTimeout(endpoint.getTimeout());
        return client;
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected HttpEndpoint endpoint;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map methods;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    private HostConfiguration getHostConfiguration(String locationURI) throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                            endpoint.getSsl(),
                            endpoint.getKeystoreManager());
            Protocol protocol = new Protocol("https", sf, 443);
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient());
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration() {
        HttpComponent comp = (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected HttpEndpoint endpoint;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
            method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        }
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                }
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                        msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    }
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    private HostConfiguration getHostConfiguration(String locationURI) throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    endpoint.getKeystoreManager());
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient());
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void start() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration() {
        HttpComponent comp = (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        return comp.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.component.ComponentLifeCycle;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.http.HttpLifeCycle;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor implements ExchangeProcessor {

    protected HttpEndpoint endpoint;
    protected HostConfiguration host;
    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private String relUri;
    private Map methods;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        java.net.URI uri = java.net.URI.create(endpoint.getLocationURI());
        relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        this.methods = new ConcurrentHashMap();
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || 
            exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = (PostMethod) methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }
        PostMethod method = new PostMethod(relUri);
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (Iterator it = headers.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        method.removeRequestHeader(Constants.HEADER_CONTENT_TYPE);
        method.addRequestHeader(Constants.HEADER_CONTENT_TYPE, entity.getContentType());
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(Constants.HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(Constants.HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(Constants.HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(Constants.HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Uncomment to avoid the http request being sent several times.
            // Can be useful when debugging
            //================================
            //method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            //    public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
            //        return false;
            //    }
            //});
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials( getClient() );
            }
            int response = getClient().executeMethod(host, method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (exchange instanceof InOnly == false) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                NormalizedMessage msg = exchange.createMessage();
                SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                Header contentType = method.getResponseHeader(Constants.HEADER_CONTENT_TYPE);
                soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                          contentType != null ? contentType.getValue() : null);
                context.setOutMessage(soapMessage);
                soapHelper.onAnswer(context);
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                ((InOut) exchange).setOutMessage(msg);
                if (txSync) {
                    channel.sendSync(exchange);
                } else {
                    methods.put(exchange.getExchangeId(), method);
                    channel.send(exchange);
                    close = false;
                }
            } else if (exchange instanceof InOptionalOut) {
                if (method.getResponseContentLength() == 0) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                } else {
                    NormalizedMessage msg = exchange.createMessage();
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              method.getResponseHeader(Constants.HEADER_CONTENT_TYPE).getValue());
                    context.setOutMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
                    ((InOptionalOut) exchange).setOutMessage(msg);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                }
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    public void start() throws Exception {
        URI uri = new URI(endpoint.getLocationURI(), false);
        if (uri.getScheme().equals("https")) {
            ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                            endpoint.getSsl(),
                            endpoint.getKeystoreManager());
            Protocol protocol = new Protocol("https", sf, 443);
            HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            this.host = new HostConfiguration();
            this.host.setHost(host);
        } else {
            this.host = new HostConfiguration();
            this.host.setHost(uri.getHost(), uri.getPort());
        }
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }
    
    protected HttpConfiguration getConfiguration(HttpEndpoint endpoint) {
        ComponentLifeCycle lf = endpoint.getServiceUnit().getComponent().getLifeCycle();
        return ((HttpLifeCycle) lf).getConfiguration();
    }

    public void stop() throws Exception {
    }

    protected Map getHeaders(HttpServletRequest request) {
        Map headers = new HashMap();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map getHeaders(HttpMethod method) {
        Map headers = new HashMap();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }
	
    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        HttpLifeCycle lf = (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        if (lf.getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpLifeCycle lf =  (HttpLifeCycle) endpoint.getServiceUnit().getComponent().getLifeCycle();
        return lf.getClient();
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.SoapExchangeProcessor;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements SoapExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    KeystoreManager.Proxy.create(endpoint.getKeystoreManager()));
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void init() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }

    public void shutdown() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        HttpClient client = comp.getClient();
        client.getParams().setSoTimeout(endpoint.getTimeout());
        return client;
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.http.processors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOptionalOut;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpEndpoint;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.SoapExchangeProcessor;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.soap.marshalers.SoapReader;
import org.apache.servicemix.soap.marshalers.SoapWriter;

/**
 * 
 * @author Guillaume Nodet
 * @version $Revision: 370186 $
 * @since 3.0
 */
public class ProviderProcessor extends AbstractProcessor implements SoapExchangeProcessor {

    private static Log log = LogFactory.getLog(ProviderProcessor.class);

    protected SoapHelper soapHelper;
    protected DeliveryChannel channel;
    private Map<String, PostMethod> methods;
    private Protocol protocol;
    
    public ProviderProcessor(HttpEndpoint endpoint) {
        super(endpoint);
        this.soapHelper = new SoapHelper(endpoint);
        this.methods = new ConcurrentHashMap<String, PostMethod>();
    }

    private String getRelUri(String locationUri) {
        java.net.URI uri = java.net.URI.create(locationUri);
        String relUri = uri.getPath();
        if (!relUri.startsWith("/")) {
            relUri = "/" + relUri;
        }
        if (uri.getQuery() != null) {
            relUri += "?" + uri.getQuery();
        }
        if (uri.getFragment() != null) {
            relUri += "#" + uri.getFragment();
        }
        return relUri;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.DONE || exchange.getStatus() == ExchangeStatus.ERROR) {
            PostMethod method = methods.remove(exchange.getExchangeId());
            if (method != null) {
                method.releaseConnection();
            }
            return;
        }
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        txSync |= endpoint.isSynchronous();
        NormalizedMessage nm = exchange.getMessage("in");
        if (nm == null) {
            throw new IllegalStateException("Exchange has no input message");
        }

        String locationURI = endpoint.getLocationURI();

        // Incorporated because of JIRA SM-695
        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            locationURI = (String) newDestinationURI;
            log.debug("Location URI overridden: " + locationURI);
        }

        PostMethod method = new PostMethod(getRelUri(locationURI));
        SoapMessage soapMessage = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soapMessage, nm);
        Context context = soapHelper.createContext(soapMessage);
        soapHelper.onSend(context);
        SoapWriter writer = soapHelper.getSoapMarshaler().createWriter(soapMessage);
        copyHeaderInformation(nm, method);
        RequestEntity entity = writeMessage(writer);
        // remove content-type header that may have been part of the in message
        if (!endpoint.isWantContentTypeHeaderFromExchangeIntoHttpRequest()) {
            method.removeRequestHeader(HEADER_CONTENT_TYPE);
            method.addRequestHeader(HEADER_CONTENT_TYPE, entity.getContentType());
        }
        if (entity.getContentLength() < 0) {
            method.removeRequestHeader(HEADER_CONTENT_LENGTH);
        } else {
            method.setRequestHeader(HEADER_CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        }
        if (endpoint.isSoap() && method.getRequestHeader(HEADER_SOAP_ACTION) == null) {
            if (endpoint.getSoapAction() != null) {
                method.setRequestHeader(HEADER_SOAP_ACTION, endpoint.getSoapAction());
            } else {
                method.setRequestHeader(HEADER_SOAP_ACTION, "\"\"");
            }
        }
        method.setRequestEntity(entity);
        boolean close = true;
        try {
            // Set the retry handler
            int retries = getConfiguration().isStreamingEnabled() ? 0 : getConfiguration().getRetryCount();
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(retries, true));
            // Set authentication
            if (endpoint.getBasicAuthentication() != null) {
                endpoint.getBasicAuthentication().applyCredentials(getClient(), exchange, nm);
            }
            // Execute the HTTP method
            int response = getClient().executeMethod(getHostConfiguration(locationURI, exchange, nm), method);
            if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
                if (!(exchange instanceof InOnly)) {
                    SoapReader reader = soapHelper.getSoapMarshaler().createReader();
                    Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
                    soapMessage = reader.read(method.getResponseBodyAsStream(), 
                                              contentType != null ? contentType.getValue() : null);
                    context.setFaultMessage(soapMessage);
                    soapHelper.onAnswer(context);
                    Fault fault = exchange.createFault();
                    fault.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
                    soapHelper.getJBIMarshaler().toNMS(fault, soapMessage);
                    exchange.setFault(fault);
                    if (txSync) {
                        channel.sendSync(exchange);
                    } else {
                        methods.put(exchange.getExchangeId(), method);
                        channel.send(exchange);
                        close = false;
                    }
                    return;
                } else {
                    throw new Exception("Invalid status response: " + response);
                }
            }
            if (exchange instanceof InOut) {
                close = processInOut(exchange, method, context, txSync, close);
            } else if (exchange instanceof InOptionalOut) {
                close = processInOptionalOut(method, exchange, context, txSync, close);
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
                channel.send(exchange);
            }
        } finally {
            if (close) {
                method.releaseConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyHeaderInformation(NormalizedMessage nm, PostMethod method) {
        Map<String, String> headers = (Map<String, String>) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        if (headers != null) {
            for (String name : headers.keySet()) {
                String value = headers.get(name);
                method.addRequestHeader(name, value);
            }
        }
    }

    private boolean processInOptionalOut(PostMethod method, MessageExchange exchange, Context context, boolean txSync,
                                         boolean close) throws Exception {
        if (method.getResponseContentLength() == 0) {
            exchange.setStatus(ExchangeStatus.DONE);
            channel.send(exchange);
        } else {
            NormalizedMessage msg = exchange.createMessage();
            SoapReader reader = soapHelper.getSoapMarshaler().createReader();
            SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(),
                                      method.getResponseHeader(HEADER_CONTENT_TYPE).getValue());
            context.setOutMessage(soapMessage);
            soapHelper.onAnswer(context);
            if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
                msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
            }
            soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
            ((InOptionalOut) exchange).setOutMessage(msg);
            if (txSync) {
                channel.sendSync(exchange);
            } else {
                methods.put(exchange.getExchangeId(), method);
                channel.send(exchange);
                close = false;
            }
        }
        return close;
    }

    private boolean processInOut(MessageExchange exchange, PostMethod method, Context context, boolean txSync,
                                 boolean close) throws Exception {
        NormalizedMessage msg = exchange.createMessage();
        SoapReader reader = soapHelper.getSoapMarshaler().createReader();
        Header contentType = method.getResponseHeader(HEADER_CONTENT_TYPE);
        SoapMessage soapMessage = reader.read(method.getResponseBodyAsStream(), contentType != null ? contentType.getValue() : null);
        context.setOutMessage(soapMessage);
        soapHelper.onAnswer(context);
        if (getConfiguration().isWantHeadersFromHttpIntoExchange()) {
            msg.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(method));
        }
        soapHelper.getJBIMarshaler().toNMS(msg, soapMessage);
        ((InOut) exchange).setOutMessage(msg);
        if (txSync) {
            channel.sendSync(exchange);
        } else {
            methods.put(exchange.getExchangeId(), method);
            channel.send(exchange);
            close = false;
        }
        return close;
    }

    private HostConfiguration getHostConfiguration(String locationURI, MessageExchange exchange, NormalizedMessage message) 
        throws Exception {
        HostConfiguration host;
        URI uri = new URI(locationURI, false);
        if (uri.getScheme().equals("https")) {
            synchronized (this) {
                if (protocol == null) {
                    ProtocolSocketFactory sf = new CommonsHttpSSLSocketFactory(
                                    endpoint.getSsl(),
                                    KeystoreManager.Proxy.create(endpoint.getKeystoreManager()));
                    protocol = new Protocol("https", sf, 443);
                }
            }
            HttpHost httphost = new HttpHost(uri.getHost(), uri.getPort(), protocol);
            host = new HostConfiguration();
            host.setHost(httphost);
        } else {
            host = new HostConfiguration();
            host.setHost(uri.getHost(), uri.getPort());
        }
        if (endpoint.getProxy() != null) {
            if ((endpoint.getProxy().getProxyHost() != null) && (endpoint.getProxy().getProxyPort() != 0)) {
                host.setProxy(endpoint.getProxy().getProxyHost(), endpoint.getProxy().getProxyPort());
            }
            if (endpoint.getProxy().getProxyCredentials() != null) {
                endpoint.getProxy().getProxyCredentials().applyProxyCredentials(getClient(), exchange, message);
            }
        } else if ((getConfiguration().getProxyHost() != null) && (getConfiguration().getProxyPort() != 0)) {
            host.setProxy(getConfiguration().getProxyHost(), getConfiguration().getProxyPort());
        }
        return host;
    }

    public void init() throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }

    public void shutdown() throws Exception {
    }

    protected Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<String, String>();
        Enumeration<?> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        return headers;
    }

    protected Map<String, String> getHeaders(HttpMethod method) {
        Map<String, String> headers = new HashMap<String, String>();
        Header[] h = method.getResponseHeaders();
        for (int i = 0; i < h.length; i++) {
            headers.put(h[i].getName(), h[i].getValue());
        }
        return headers;
    }

    protected RequestEntity writeMessage(SoapWriter writer) throws Exception {
        if (getConfiguration().isStreamingEnabled()) {
            return new StreamingRequestEntity(writer);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(baos);
            return new ByteArrayRequestEntity(baos.toByteArray(), writer.getContentType());
        }
    }

    protected HttpClient getClient() {
        HttpComponent comp =  (HttpComponent) endpoint.getServiceUnit().getComponent();
        HttpClient client = comp.getClient();
        client.getParams().setSoTimeout(endpoint.getTimeout());
        return client;
    }

    public static class StreamingRequestEntity implements RequestEntity {

        private SoapWriter writer;
        
        public StreamingRequestEntity(SoapWriter writer) {
            this.writer = writer;
        }
        
        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            try {
                writer.write(out);
                out.flush();
            } catch (Exception e) {
                throw (IOException) new IOException("Could not write request").initCause(e);
            }
        }

        public long getContentLength() {
            // not known so we send negative value
            return -1;
        }

        public String getContentType() {
            return writer.getContentType();
        }
        
    }
}

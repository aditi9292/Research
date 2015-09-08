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
package org.apache.servicemix.http;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.ExchangeStatus;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.components.http.InvalidStatusResponseException;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.components.util.MockServiceComponent;
import org.apache.servicemix.components.util.TransformComponentSupport;
import org.apache.servicemix.http.endpoints.HttpConsumerEndpoint;
import org.apache.servicemix.http.endpoints.HttpSoapConsumerEndpoint;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jbi.messaging.MessageExchangeSupport;
import org.apache.servicemix.jbi.messaging.MessageExchangeImpl;
import org.apache.servicemix.jbi.util.DOMUtil;
import org.apache.servicemix.soap.bindings.soap.Soap11;
import org.apache.servicemix.soap.bindings.soap.Soap12;
import org.apache.servicemix.soap.bindings.soap.SoapConstants;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.apache.servicemix.soap.util.DomUtil;
import org.apache.servicemix.tck.ReceiverComponent;
import org.apache.servicemix.tck.ExchangeCompletedListener;
import org.apache.servicemix.executors.impl.ExecutorFactoryImpl;
import org.apache.xpath.CachedXPathAPI;
import org.springframework.core.io.ClassPathResource;
import org.mortbay.jetty.bio.SocketConnector;

public class ConsumerEndpointTest extends TestCase {
    private static transient Log log = LogFactory.getLog(ConsumerEndpointTest.class);

    protected JBIContainer container;
    protected SourceTransformer transformer = new SourceTransformer();

    static {
        System.setProperty("org.apache.servicemix.preserveContent", "true");
    }

    protected void setUp() throws Exception {
        container = new JBIContainer();
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setEmbedded(true);
        ExecutorFactoryImpl factory = new ExecutorFactoryImpl();
        factory.getDefaultConfig().setQueueSize(0);
        container.setExecutorFactory(factory);
        container.init();
    }

    protected void tearDown() throws Exception {
        if (container != null) {
            container.shutDown();
        }
    }

    protected String textValueOfXPath(Node node, String xpath) throws TransformerException {
        CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
        NodeIterator iterator = cachedXPathAPI.selectNodeIterator(node, xpath);
        Node root = iterator.nextNode();
        if (root instanceof Element) {
            Element element = (Element) root;
            if (element == null) {
                return "";
            }
            return DOMUtil.getElementText(element);
        } else if (root != null) {
            return root.getNodeValue();
        } else {
            return null;
        }
    }

    public void testHttpInOnly() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "recv"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setDefaultMep(MessageExchangeSupport.IN_ONLY);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        ReceiverComponent recv = new ReceiverComponent();
        recv.setService(new QName("urn:test", "recv"));
        container.activateComponent(recv, "recv");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        assertEquals("", res);
        if (post.getStatusCode() != 202) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }

        recv.getMessageList().assertMessagesReceived(1);
    }

    public void testHttpInOutWithTimeout() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setDefaultMep(MessageExchangeSupport.IN_OUT);
        ep.setTimeout(1000);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent() {
            public void onMessageExchange(MessageExchange exchange) {
                super.onMessageExchange(exchange);
            }
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                return super.transform(exchange, in, out);
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        if (post.getStatusCode() != 500) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
        Thread.sleep(1000);
    }

    public void testHttpInOut() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Node node = transformer.toDOMNode(new StringSource(res));
        log.info(transformer.toString(node));
        assertEquals("world", textValueOfXPath(node, "/hello/text()"));
        if (post.getStatusCode() != 200) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
    }

    protected void initSoapEndpoints(boolean useJbiWrapper) throws Exception {
        HttpComponent http = new HttpComponent();
        HttpSoapConsumerEndpoint ep1 = new HttpSoapConsumerEndpoint();
        ep1.setService(new QName("uri:HelloWorld", "HelloService"));
        ep1.setEndpoint("HelloPortSoap11");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setLocationURI("http://localhost:8192/ep1/");
        ep1.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep1.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep1.setUseJbiWrapper(useJbiWrapper);
        HttpSoapConsumerEndpoint ep2 = new HttpSoapConsumerEndpoint();
        ep2.setService(new QName("uri:HelloWorld", "HelloService"));
        ep2.setEndpoint("HelloPortSoap12");
        ep2.setTargetService(new QName("urn:test", "echo"));
        ep2.setLocationURI("http://localhost:8192/ep2/");
        ep2.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep2.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep2.setUseJbiWrapper(useJbiWrapper);
        http.setEndpoints(new HttpEndpointType[] {ep1, ep2});
        container.activateComponent(http, "http");
        container.start();
    }

    public void testHttpSoap11FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    public void testHttpSoap12FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTCODE, DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTVALUE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_SENDER, DomUtil.createQName(elem, elem.getTextContent()));
        elem = DomUtil.getNextSiblingElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTSUBCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    public void testHttpSoap11UnkownOp() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Body><hello>world</hello></s:Body>" + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_CLIENT, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    /*
     * public void testHttpSoapAttachments() throws Exception { initSoapEndpoints(true);
     * 
     * HttpComponent http = new HttpComponent(); HttpEndpoint ep0 = new HttpEndpoint(); ep0.setService(new
     * QName("urn:test", "s0")); ep0.setEndpoint("ep0"); ep0.setLocationURI("http://localhost:8192/ep1/");
     * ep0.setRoleAsString("provider"); ep0.setSoapVersion("1.1"); ep0.setSoap(true); http.setEndpoints(new
     * HttpEndpoint[] { ep0 }); container.activateComponent(http, "http2");
     * 
     * MockServiceComponent echo = new MockServiceComponent(); echo.setService(new QName("urn:test", "echo"));
     * echo.setEndpoint("endpoint"); echo.setResponseXml("<jbi:message
     * xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'><jbi:part><HelloResponse xmlns='uri:HelloWorld' />
     * </jbi:part></jbi:message>");
     * container.activateComponent(echo, "echo");
     * 
     * DefaultServiceMixClient client = new DefaultServiceMixClient(container); Destination d =
     * client.createDestination("service:urn:test:s0"); InOut me = d.createInOutExchange();
     * me.getInMessage().setContent(new StringSource("<HelloRequest xmlns='uri:HelloWorld'/>")); Map<QName,
     * DocumentFragment> headers = new HashMap<QName, DocumentFragment>(); Document doc = DOMUtil.newDocument();
     * DocumentFragment fragment = doc.createDocumentFragment(); DomUtil.createElement(fragment, new
     * QName("uri:HelloWorld", "HelloHeader")); headers.put(new QName("uri:HelloWorld", "HelloHeader"), fragment);
     * me.getInMessage().setProperty(org.apache.servicemix.JbiConstants.SOAP_HEADERS, headers); File f = new
     * File(getClass().getResource("servicemix.jpg").getFile()); ByteArrayOutputStream baos = new
     * ByteArrayOutputStream(); FileUtil.copyInputStream(new FileInputStream(f), baos); DataSource ds = new
     * ByteArrayDataSource(baos.toByteArray(), "image/jpeg"); DataHandler dh = new DataHandler(ds);
     * me.getInMessage().addAttachment("image", dh); client.sendSync(me); assertEquals(ExchangeStatus.ACTIVE,
     * me.getStatus()); assertNull(me.getFault()); assertEquals(1, me.getOutMessage().getAttachmentNames().size());
     * client.done(me); }
     */

    public void testHttpSoap11() throws Exception {
        initSoapEndpoints(true);

        MockServiceComponent echo = new MockServiceComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        echo.setResponseXml("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                        + "<jbi:part><HelloResponse xmlns='uri:HelloWorld' /></jbi:part>" + "</jbi:message>");
        container.activateComponent(echo, "echo");

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                        + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                        + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    public void testHttpSoap12() throws Exception {
        initSoapEndpoints(true);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    log.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(JbiConstants.WSDL11_WRAPPER_MESSAGE, DomUtil.getQName(elem));
                out.setContent(
                         new StringSource("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                                   + "<jbi:part><HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse></jbi:part>"
                                   + "</jbi:message> "));
                return true;
            }
        };
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    public void testHttpSoap12WithoutJbiWrapper() throws Exception {
        initSoapEndpoints(false);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    log.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(new QName("uri:HelloWorld", "HelloRequest"), DomUtil.getQName(elem));
                out.setContent(new StringSource("<HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse>"));
                return true;
            }
        };
        mock.setCopyProperties(false);
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    /*
     * Load testing test
     */
    public void testHttpInOutUnderLoad() throws Exception {
        final int nbThreads = 16;
        final int nbRequests = 8;
        final int endpointTimeout = 100;
        final int echoSleepTime = 90;
        final int soTimeout = 60 * 1000 * 1000;
        final int listenerTimeout = 5000;

        ExchangeCompletedListener listener = new ExchangeCompletedListener(listenerTimeout);
        container.addListener(listener);

        HttpComponent http = new HttpComponent();
        //http.getConfiguration().setJettyConnectorClassName(SocketConnector.class.getName());
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setTimeout(endpointTimeout);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        final CountDownLatch latchRecv = new CountDownLatch(nbThreads * nbRequests);
        EchoComponent echo = new EchoComponent() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                latchRecv.countDown();
                try {
                    Thread.sleep(echoSleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                out.setContent(in.getContent());
                return true;
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        ((ExecutorFactoryImpl) container.getExecutorFactory()).getDefaultConfig().setMaximumPoolSize(16);

        container.start();

        final List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>();
        final CountDownLatch latchSent = new CountDownLatch(nbThreads * nbRequests);
        for (int t = 0; t < nbThreads; t++) {
            new Thread() {
                public void run() {
                    final SourceTransformer transformer = new SourceTransformer(); 
                    final HttpClient client = new HttpClient();
                    client.getParams().setSoTimeout(soTimeout);
                    for (int i = 0; i < nbRequests; i++) {
                        try {
                            PostMethod post = new PostMethod("http://localhost:8192/ep1/");
                            post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
                            client.executeMethod(post);
                            if (post.getStatusCode() != 200) {
                                throw new InvalidStatusResponseException(post.getStatusCode());
                            }
                            Node node = transformer.toDOMNode(new StreamSource(post.getResponseBodyAsStream()));
                            log.info(transformer.toString(node));
                            assertEquals("world", textValueOfXPath(node, "/hello/text()"));
                        } catch (Throwable t) {
                            throwables.add(t);
                        } finally {
                            latchSent.countDown();
                            //System.out.println("[" + System.currentTimeMillis() + "] Request " + latch.getCount() + " processed");
                        }
                    }
                }
            }.start();
        }
        latchSent.await();
        latchRecv.await();
        listener.assertExchangeCompleted();
        for (Throwable t : throwables) {
            t.printStackTrace();
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
package org.apache.servicemix.http;

import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.components.http.InvalidStatusResponseException;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.components.util.MockServiceComponent;
import org.apache.servicemix.components.util.TransformComponentSupport;
import org.apache.servicemix.http.endpoints.HttpConsumerEndpoint;
import org.apache.servicemix.http.endpoints.HttpSoapConsumerEndpoint;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jbi.messaging.MessageExchangeSupport;
import org.apache.servicemix.jbi.util.DOMUtil;
import org.apache.servicemix.soap.bindings.soap.Soap11;
import org.apache.servicemix.soap.bindings.soap.Soap12;
import org.apache.servicemix.soap.bindings.soap.SoapConstants;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.apache.servicemix.soap.util.DomUtil;
import org.apache.servicemix.tck.ReceiverComponent;
import org.apache.servicemix.tck.ExchangeCompletedListener;
import org.apache.servicemix.executors.impl.ExecutorFactoryImpl;
import org.apache.xpath.CachedXPathAPI;
import org.springframework.core.io.ClassPathResource;
import org.mortbay.jetty.HttpHeaders;

public class ConsumerEndpointTest extends TestCase {
    private static transient Log log = LogFactory.getLog(ConsumerEndpointTest.class);

    protected JBIContainer container;
    protected SourceTransformer transformer = new SourceTransformer();

    static {
        System.setProperty("org.apache.servicemix.preserveContent", "true");
    }

    protected void setUp() throws Exception {
        container = new JBIContainer();
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setEmbedded(true);
        ExecutorFactoryImpl factory = new ExecutorFactoryImpl();
        factory.getDefaultConfig().setQueueSize(0);
        container.setExecutorFactory(factory);
        container.init();
    }

    protected void tearDown() throws Exception {
        if (container != null) {
            container.shutDown();
        }
    }

    protected String textValueOfXPath(Node node, String xpath) throws TransformerException {
        CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
        NodeIterator iterator = cachedXPathAPI.selectNodeIterator(node, xpath);
        Node root = iterator.nextNode();
        if (root instanceof Element) {
            Element element = (Element) root;
            if (element == null) {
                return "";
            }
            return DOMUtil.getElementText(element);
        } else if (root != null) {
            return root.getNodeValue();
        } else {
            return null;
        }
    }

    public void testHttpInOnly() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "recv"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setDefaultMep(MessageExchangeSupport.IN_ONLY);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        ReceiverComponent recv = new ReceiverComponent();
        recv.setService(new QName("urn:test", "recv"));
        container.activateComponent(recv, "recv");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        assertEquals("", res);
        if (post.getStatusCode() != 202) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }

        recv.getMessageList().assertMessagesReceived(1);
    }

    public void testHttpInOutWithTimeout() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setDefaultMep(MessageExchangeSupport.IN_OUT);
        ep.setTimeout(1000);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent() {
            public void onMessageExchange(MessageExchange exchange) {
                super.onMessageExchange(exchange);
            }
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                return super.transform(exchange, in, out);
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        if (post.getStatusCode() != 500) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
        Thread.sleep(1000);
    }

    public void testHttpInOut() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Node node = transformer.toDOMNode(new StringSource(res));
        log.info(transformer.toString(node));
        assertEquals("world", textValueOfXPath(node, "/hello/text()"));
        if (post.getStatusCode() != 200) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
    }

    protected void initSoapEndpoints(boolean useJbiWrapper) throws Exception {
        HttpComponent http = new HttpComponent();
        HttpSoapConsumerEndpoint ep1 = new HttpSoapConsumerEndpoint();
        ep1.setService(new QName("uri:HelloWorld", "HelloService"));
        ep1.setEndpoint("HelloPortSoap11");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setLocationURI("http://localhost:8192/ep1/");
        ep1.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep1.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep1.setUseJbiWrapper(useJbiWrapper);
        HttpSoapConsumerEndpoint ep2 = new HttpSoapConsumerEndpoint();
        ep2.setService(new QName("uri:HelloWorld", "HelloService"));
        ep2.setEndpoint("HelloPortSoap12");
        ep2.setTargetService(new QName("urn:test", "echo"));
        ep2.setLocationURI("http://localhost:8192/ep2/");
        ep2.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep2.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep2.setUseJbiWrapper(useJbiWrapper);
        http.setEndpoints(new HttpEndpointType[] {ep1, ep2});
        container.activateComponent(http, "http");
        container.start();
    }

    public void testHttpSoap11FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    public void testHttpSoap12FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTCODE, DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTVALUE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_SENDER, DomUtil.createQName(elem, elem.getTextContent()));
        elem = DomUtil.getNextSiblingElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTSUBCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    public void testHttpSoap11UnkownOp() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Body><hello>world</hello></s:Body>" + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_CLIENT, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
    }

    /*
     * public void testHttpSoapAttachments() throws Exception { initSoapEndpoints(true);
     * 
     * HttpComponent http = new HttpComponent(); HttpEndpoint ep0 = new HttpEndpoint(); ep0.setService(new
     * QName("urn:test", "s0")); ep0.setEndpoint("ep0"); ep0.setLocationURI("http://localhost:8192/ep1/");
     * ep0.setRoleAsString("provider"); ep0.setSoapVersion("1.1"); ep0.setSoap(true); http.setEndpoints(new
     * HttpEndpoint[] { ep0 }); container.activateComponent(http, "http2");
     * 
     * MockServiceComponent echo = new MockServiceComponent(); echo.setService(new QName("urn:test", "echo"));
     * echo.setEndpoint("endpoint"); echo.setResponseXml("<jbi:message
     * xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'><jbi:part><HelloResponse xmlns='uri:HelloWorld' />
     * </jbi:part></jbi:message>");
     * container.activateComponent(echo, "echo");
     * 
     * DefaultServiceMixClient client = new DefaultServiceMixClient(container); Destination d =
     * client.createDestination("service:urn:test:s0"); InOut me = d.createInOutExchange();
     * me.getInMessage().setContent(new StringSource("<HelloRequest xmlns='uri:HelloWorld'/>")); Map<QName,
     * DocumentFragment> headers = new HashMap<QName, DocumentFragment>(); Document doc = DOMUtil.newDocument();
     * DocumentFragment fragment = doc.createDocumentFragment(); DomUtil.createElement(fragment, new
     * QName("uri:HelloWorld", "HelloHeader")); headers.put(new QName("uri:HelloWorld", "HelloHeader"), fragment);
     * me.getInMessage().setProperty(org.apache.servicemix.JbiConstants.SOAP_HEADERS, headers); File f = new
     * File(getClass().getResource("servicemix.jpg").getFile()); ByteArrayOutputStream baos = new
     * ByteArrayOutputStream(); FileUtil.copyInputStream(new FileInputStream(f), baos); DataSource ds = new
     * ByteArrayDataSource(baos.toByteArray(), "image/jpeg"); DataHandler dh = new DataHandler(ds);
     * me.getInMessage().addAttachment("image", dh); client.sendSync(me); assertEquals(ExchangeStatus.ACTIVE,
     * me.getStatus()); assertNull(me.getFault()); assertEquals(1, me.getOutMessage().getAttachmentNames().size());
     * client.done(me); }
     */

    public void testHttpSoap11() throws Exception {
        initSoapEndpoints(true);

        MockServiceComponent echo = new MockServiceComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        echo.setResponseXml("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                        + "<jbi:part><HelloResponse xmlns='uri:HelloWorld' /></jbi:part>" + "</jbi:message>");
        container.activateComponent(echo, "echo");

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                        + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                        + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    public void testHttpSoap12() throws Exception {
        initSoapEndpoints(true);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    log.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(JbiConstants.WSDL11_WRAPPER_MESSAGE, DomUtil.getQName(elem));
                out.setContent(
                         new StringSource("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                                   + "<jbi:part><HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse></jbi:part>"
                                   + "</jbi:message> "));
                return true;
            }
        };
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    public void testHttpSoap12WithoutJbiWrapper() throws Exception {
        initSoapEndpoints(false);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    log.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(new QName("uri:HelloWorld", "HelloRequest"), DomUtil.getQName(elem));
                out.setContent(new StringSource("<HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse>"));
                return true;
            }
        };
        mock.setCopyProperties(false);
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        log.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    public void testGzipEncodingNonSoap() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        http.setEndpoints(new HttpEndpointType[] {ep });
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:8192/ep1/");
        post.addRequestHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        post.addRequestHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.write("<hello>world</hello>".getBytes());
        gos.flush();
        gos.close();

        post.setRequestEntity(new ByteArrayRequestEntity(baos.toByteArray()));
        new HttpClient().executeMethod(post);

        GZIPInputStream gis = new GZIPInputStream(post.getResponseBodyAsStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(gis));

        String result = br.readLine();
        log.info(result);
        Node node = transformer.toDOMNode(new StringSource(result));
        log.info(transformer.toString(node));
        assertEquals("world", textValueOfXPath(node, "/hello/text()"));
        if (post.getStatusCode() != 200) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
    }

    public void testGzipEncodingSoap() throws Exception {
        initSoapEndpoints(true);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    log.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(JbiConstants.WSDL11_WRAPPER_MESSAGE, DomUtil.getQName(elem));
                out.setContent(
                    new StringSource("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                        + "<jbi:part><HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse></jbi:part>"
                        + "</jbi:message> "));
                return true;
            }
        };
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:8192/ep2/");

        post.addRequestHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        post.addRequestHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);

        gos.write(("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
            + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
            + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
            + "</s:Envelope>").getBytes());
        gos.flush();
        gos.close();

        post.setRequestEntity(new ByteArrayRequestEntity(baos.toByteArray()));
        new HttpClient().executeMethod(post);

        GZIPInputStream gis = new GZIPInputStream(post.getResponseBodyAsStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(gis));

        String result = br.readLine();
        log.info(result);
        Element elem = transformer.toDOMElement(new StringSource(result));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
    }

    /*
     * Load testing test
     */
    public void testHttpInOutUnderLoad() throws Exception {
        final int nbThreads = 16;
        final int nbRequests = 8;
        final int endpointTimeout = 100;
        final int echoSleepTime = 90;
        final int soTimeout = 60 * 1000 * 1000;
        final int listenerTimeout = 5000;

        ExchangeCompletedListener listener = new ExchangeCompletedListener(listenerTimeout);
        container.addListener(listener);

        HttpComponent http = new HttpComponent();
        //http.getConfiguration().setJettyConnectorClassName(SocketConnector.class.getName());
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:8192/ep1/");
        ep.setTimeout(endpointTimeout);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        final CountDownLatch latchRecv = new CountDownLatch(nbThreads * nbRequests);
        EchoComponent echo = new EchoComponent() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                latchRecv.countDown();
                try {
                    Thread.sleep(echoSleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                out.setContent(in.getContent());
                return true;
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        ((ExecutorFactoryImpl) container.getExecutorFactory()).getDefaultConfig().setMaximumPoolSize(16);

        container.start();

        final List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>();
        final CountDownLatch latchSent = new CountDownLatch(nbThreads * nbRequests);
        for (int t = 0; t < nbThreads; t++) {
            new Thread() {
                public void run() {
                    final SourceTransformer transformer = new SourceTransformer(); 
                    final HttpClient client = new HttpClient();
                    client.getParams().setSoTimeout(soTimeout);
                    for (int i = 0; i < nbRequests; i++) {
                        try {
                            PostMethod post = new PostMethod("http://localhost:8192/ep1/");
                            post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
                            client.executeMethod(post);
                            if (post.getStatusCode() != 200) {
                                throw new InvalidStatusResponseException(post.getStatusCode());
                            }
                            Node node = transformer.toDOMNode(new StreamSource(post.getResponseBodyAsStream()));
                            log.info(transformer.toString(node));
                            assertEquals("world", textValueOfXPath(node, "/hello/text()"));
                        } catch (Throwable t) {
                            throwables.add(t);
                        } finally {
                            latchSent.countDown();
                            //System.out.println("[" + System.currentTimeMillis() + "] Request " + latch.getCount() + " processed");
                        }
                    }
                }
            }.start();
        }
        latchSent.await();
        latchRecv.await();
        listener.assertExchangeCompleted();
        for (Throwable t : throwables) {
            t.printStackTrace();
        }
    }

}

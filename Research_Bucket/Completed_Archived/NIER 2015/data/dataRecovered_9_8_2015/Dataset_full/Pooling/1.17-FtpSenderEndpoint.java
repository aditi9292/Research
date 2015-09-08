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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;
    private boolean checkDuplicates = true;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public boolean isCheckDuplicates() {
        return checkDuplicates;
    }

    public void setCheckDuplicates(boolean checkDuplicates) {
        this.checkDuplicates = checkDuplicates;
    }

    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (checkDuplicates && client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;
    private boolean checkDuplicates = true;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public boolean isCheckDuplicates() {
        return checkDuplicates;
    }

    public void setCheckDuplicates(boolean checkDuplicates) {
        this.checkDuplicates = checkDuplicates;
    }

    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (checkDuplicates && client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite = false;
    private URI uri;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            String name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                }
                else {
                    out = client.storeUniqueFileStream();
                }
            }
            else {
                out = client.storeFileStream(name);
                if (out == null) {
                    // lets try overwrite the previous file?
                    if (overwrite) {
                        client.deleteFile(name);
                    }
                    out = client.storeFileStream(name);
                }
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + name + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, name);
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            client.completePendingCommand();
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        }
        catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            }
            catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite = false;
    private URI uri;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            String name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                }
                else {
                    out = client.storeUniqueFileStream();
                }
            }
            else {
                out = client.storeFileStream(name);
                if (out == null) {
                    // lets try overwrite the previous file?
                    if (overwrite) {
                        client.deleteFile(name);
                    }
                    out = client.storeFileStream(name);
                }
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + name + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, name);
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            client.completePendingCommand();
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        }
        catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            }
            catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite = false;
    private URI uri;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            String name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                }
                else {
                    out = client.storeUniqueFileStream();
                }
            }
            else {
                out = client.storeFileStream(name);
                if (out == null) {
                    // lets try overwrite the previous file?
                    if (overwrite) {
                        client.deleteFile(name);
                    }
                    out = client.storeFileStream(name);
                }
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + name + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, name);
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            client.completePendingCommand();
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        }
        catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            }
            catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;
    private boolean checkDuplicates = true;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public boolean isCheckDuplicates() {
        return checkDuplicates;
    }

    public void setCheckDuplicates(boolean checkDuplicates) {
        this.checkDuplicates = checkDuplicates;
    }

    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (checkDuplicates && client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite = false;
    private URI uri;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }


    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                client.changeWorkingDirectory(uri.getPath());
            }

            String name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                }
                else {
                    out = client.storeUniqueFileStream();
                }
            }
            else {
                out = client.storeFileStream(name);
                if (out == null) {
                    // lets try overwrite the previous file?
                    if (overwrite) {
                        client.deleteFile(name);
                    }
                    out = client.storeFileStream(name);
                }
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + name + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, name);
        } 
        finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                }
                catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        }
        catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            }
            catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;
    private boolean checkDuplicates = true;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public boolean isCheckDuplicates() {
        return checkDuplicates;
    }

    public void setCheckDuplicates(boolean checkDuplicates) {
        this.checkDuplicates = checkDuplicates;
    }

    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                if (!client.changeWorkingDirectory(uri.getPath())) {
                    logger.warn("Unable to change ftp directory to '" + uri.getPath() + "'");
                }
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (checkDuplicates && client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
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
package org.apache.servicemix.ftp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * An FTP endpoint
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="sender"
 */
public class FtpSenderEndpoint extends ProviderEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String uniqueFileName = "ServiceMix";
    private boolean overwrite;
    private URI uri;
    private String uploadSuffix;
    private boolean checkDuplicates = true;

    public FtpSenderEndpoint() {
    }

    public FtpSenderEndpoint(FtpComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }
    
    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
    }

    /**
     * Configures the endpoint from a URI
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public boolean isCheckDuplicates() {
        return checkDuplicates;
    }

    public void setCheckDuplicates(boolean checkDuplicates) {
        this.checkDuplicates = checkDuplicates;
    }

    public void start() throws Exception {
        super.start();
        if (clientPool == null) {
            clientPool = createClientPool();
        }
        if (uri != null) {
            clientPool.setHost(uri.getHost());
            clientPool.setPort(uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":");
                clientPool.setUsername(infos[0]);
                if (infos.length > 1) {
                    clientPool.setPassword(infos[1]);
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getUniqueFileName() {
        return uniqueFileName;
    }

    /**
     * Sets the name used to make a unique name if no file name is available on the message.
     *
     * @param uniqueFileName the new value of the unique name to use for generating unique names
     */
    public void setUniqueFileName(String uniqueFileName) {
        this.uniqueFileName = uniqueFileName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getUploadSuffix() {
        return uploadSuffix;
    }

    /**
     * Set the file name suffix used during upload.  The suffix will be automatically removed as soon as the upload has completed.  
     * This allows other processes to discern completed files from files that are being uploaded.
     * 
     * @param uploadSuffix
     */
    public void setUploadSuffix(String uploadSuffix) {
        this.uploadSuffix = uploadSuffix;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void processInOnly(MessageExchange exchange, NormalizedMessage message) throws Exception {
        FTPClient client = null;
        OutputStream out = null;
        String name = null;
        String uploadName = null;
        try {
            client = borrowClient();
            // Change to the directory specified by the URI path if any
            if (uri != null && uri.getPath() != null) {
                if (!client.changeWorkingDirectory(uri.getPath())) {
                    logger.warn("Unable to change ftp directory to '" + uri.getPath() + "'");
                }
            }

            name = marshaler.getOutputName(exchange, message);
            if (name == null) {
                if (uniqueFileName != null) {
                    out = client.storeUniqueFileStream(uniqueFileName);
                } else {
                    out = client.storeUniqueFileStream();
                }
            } else {
                if (checkDuplicates && client.listFiles(name).length > 0) {
                    if (overwrite) {
                        client.deleteFile(name);
                    } else {
                        throw new IOException("Can not send " + name
                                + " : file already exists and overwrite has not been enabled");
                    }
                }
                uploadName = uploadSuffix == null ? name : name + uploadSuffix;
                out = client.storeFileStream(uploadName);
            }
            if (out == null) {
                throw new IOException("No output stream available for output name: " + uploadName + ". Maybe the file already exists?");
            }
            marshaler.writeMessage(exchange, message, out, uploadName);
        } finally {
            if (out != null) {
                try {
                    out.close();
                    client.completePendingCommand();
                    if (name != null && !name.equals(uploadName) && !client.rename(uploadName, name)) {
                        throw new IOException("File " + uploadName + " could not be renamed to " + name);
                    }
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: " + e, e);
                }
            }
            returnClient(client);
        }
    }

    protected FTPClientPool createClientPool() throws Exception {
        FTPClientPool pool = new FTPClientPool();
        pool.afterPropertiesSet();
        return pool;
    }

    protected FTPClient borrowClient() throws JBIException {
        try {
            return (FTPClient) getClientPool().borrowClient();
        } catch (Exception e) {
            throw new JBIException(e);
        }
    }

    protected void returnClient(FTPClient client) {
        if (client != null) {
            try {
                getClientPool().returnClient(client);
            } catch (Exception e) {
                logger.error("Failed to return client to pool: " + e, e);
            }
        }
    }
}

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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean changeWorkingDirectory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private QName targetOperation;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
        if (changeWorkingDirectory && recursive) {
            throw new DeploymentException("changeWorkingDirectory='true' can not be set when recursive='true'");
        }
    }
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public QName getTargetOperation() { return targetOperation; }

    public void setTargetOperation(QName targetOperation) { this.targetOperation = targetOperation; }

    public void setChangeWorkingDirectory(boolean changeWorkingDirectory) {
        this.changeWorkingDirectory = changeWorkingDirectory;
    }
    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = listFiles(ftp, fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    private FTPFile[] listFiles(FTPClient ftp, String directory) throws IOException {
        if (changeWorkingDirectory) {
            ftp.changeWorkingDirectory(directory);
            return ftp.listFiles("");
        } else {
            return ftp.listFiles(directory);
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        if (getTargetOperation() != null) { exchange.setOperation(getTargetOperation()); }
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean changeWorkingDirectory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private QName targetOperation;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
        if (changeWorkingDirectory && recursive) {
            throw new DeploymentException("changeWorkingDirectory='true' can not be set when recursive='true'");
        }
    }
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public QName getTargetOperation() { return targetOperation; }

    public void setTargetOperation(QName targetOperation) { this.targetOperation = targetOperation; }

    public void setChangeWorkingDirectory(boolean changeWorkingDirectory) {
        this.changeWorkingDirectory = changeWorkingDirectory;
    }
    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = listFiles(ftp, fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    private FTPFile[] listFiles(FTPClient ftp, String directory) throws IOException {
        if (changeWorkingDirectory) {
            ftp.changeWorkingDirectory(directory);
            return ftp.listFiles("");
        } else {
            return ftp.listFiles(directory);
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        if (getTargetOperation() != null) { exchange.setOperation(getTargetOperation()); }
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

import edu.emory.mathcs.backport.java.util.concurrent.locks.Lock;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        }
        finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.equals(".") || name.equals("..")) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    }
                    finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
            	//avoid processing files that have been deleted on the server
            	logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        }
        catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

import edu.emory.mathcs.backport.java.util.concurrent.locks.Lock;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, true);
        }
        finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.equals(".") || name.equals("..")) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    }
                    finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            // Process the file. If processing fails, an exception should be thrown.
            processFile(ftp, file);
            // Processing is succesfull
            // We should not unlock until the file has been deleted
            unlock = false;
            if (isDeleteFile()) {
                if (!ftp.deleteFile(file)) {
                    throw new IOException("Could not delete file " + file);
                }
                unlock = true;
            }
        }
        catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

import edu.emory.mathcs.backport.java.util.concurrent.locks.Lock;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, true);
        }
        finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.equals(".") || name.equals("..")) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    }
                    finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            // Process the file. If processing fails, an exception should be thrown.
            processFile(ftp, file);
            // Processing is succesfull
            // We should not unlock until the file has been deleted
            unlock = false;
            if (isDeleteFile()) {
                if (!ftp.deleteFile(file)) {
                    throw new IOException("Could not delete file " + file);
                }
                unlock = true;
            }
        }
        catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean changeWorkingDirectory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private QName targetOperation;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
        if (changeWorkingDirectory && recursive) {
            throw new DeploymentException("changeWorkingDirectory='true' can not be set when recursive='true'");
        }
    }
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public QName getTargetOperation() { return targetOperation; }

    public void setTargetOperation(QName targetOperation) { this.targetOperation = targetOperation; }

    public void setChangeWorkingDirectory(boolean changeWorkingDirectory) {
        this.changeWorkingDirectory = changeWorkingDirectory;
    }
    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = listFiles(ftp, fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    private FTPFile[] listFiles(FTPClient ftp, String directory) throws IOException {
        if (changeWorkingDirectory) {
            ftp.changeWorkingDirectory(directory);
            return ftp.listFiles("");
        } else {
            return ftp.listFiles(directory);
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        if (getTargetOperation() != null) { exchange.setOperation(getTargetOperation()); }
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

import edu.emory.mathcs.backport.java.util.concurrent.locks.Lock;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private URI uri;

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
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
    
    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        }
        finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = ftp.listFiles(fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.equals(".") || name.equals("..")) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
            // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(file);
                    }
                    finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(String file) {
        FTPClient ftp = null;
        boolean unlock = true;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (ftp.listFiles(file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                // Processing is successful
                // We should not unlock until the file has been deleted
                unlock = false;
                if (isDeleteFile()) {
                    if (!ftp.deleteFile(file)) {
                        throw new IOException("Could not delete file " + file);
                    }
                    unlock = true;
                }
            } else {
            	//avoid processing files that have been deleted on the server
            	logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        }
        catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            returnClient(ftp);
        }
        return unlock;
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, file);
        sendSync(exchange);
        in.close();
        ftp.completePendingCommand();
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            Exception e = exchange.getError();
            if (e == null) {
                e = new JBIException("Unkown error");
            }
            throw e;
        }
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.common.locks.LockManager;
import org.apache.servicemix.common.locks.impl.SimpleLockManager;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean changeWorkingDirectory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private ConcurrentMap<String, FtpData> openExchanges = new ConcurrentHashMap<String, FtpData>();
    private QName targetOperation;
    private URI uri;
    private boolean stateless = true;

    protected class FtpData {
        final String file;
        final FTPClient ftp;
        final InputStream in;
        public FtpData(String file, FTPClient ftp, InputStream in) {
            this.file = file;
            this.ftp = ftp;
            this.in = in;
        }
    }

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
        if (changeWorkingDirectory && recursive) {
            throw new DeploymentException("changeWorkingDirectory='true' can not be set when recursive='true'");
        }
    }

    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }

    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public QName getTargetOperation() {
        return targetOperation;
    }

    public void setTargetOperation(QName targetOperation) {
        this.targetOperation = targetOperation;
    }

    public void setChangeWorkingDirectory(boolean changeWorkingDirectory) {
        this.changeWorkingDirectory = changeWorkingDirectory;
    }

    public boolean isStateless() {
        return stateless;
    }

    public void setStateless(boolean stateless) {
        this.stateless = stateless;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = listFiles(ftp, fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
                // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    private FTPFile[] listFiles(FTPClient ftp, String directory) throws IOException {
        if (changeWorkingDirectory) {
            ftp.changeWorkingDirectory(directory);
            return ftp.listFiles();
        } else {
            return ftp.listFiles(directory);
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    processFileNow(file);
                }
            }
        });
    }

    protected void processFileNow(String file) {
        FTPClient ftp = null;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (listFiles(ftp, file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                ftp = null;
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            if (ftp != null) {
                returnClient(ftp);
            }
        }
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);

        if (getTargetOperation() != null) { 
            exchange.setOperation(getTargetOperation()); 
        }

        marshaler.readMessage(exchange, message, in, file);
        if (stateless) {
            exchange.setProperty(FtpData.class.getName(), new FtpData(file, ftp, in));
        } else {
            this.openExchanges.put(exchange.getExchangeId(), new FtpData(file, ftp, in));
        }
        send(exchange);
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        FtpData data;
        if (stateless) {
            data = (FtpData) exchange.getProperty(FtpData.class.getName());
        } else {
            data = this.openExchanges.remove(exchange.getExchangeId());
        }
        // check for done or error
        if (data != null) {
            logger.debug("Releasing " + data.file);
            try {
                // Close ftp related stuff
                data.in.close();
                data.ftp.completePendingCommand();
                // check for state
                if (exchange.getStatus() == ExchangeStatus.DONE) {
                    if (isDeleteFile()) {
                        if (!data.ftp.deleteFile(data.file)) {
                            throw new IOException("Could not delete file " + data.file);
                        }
                    }
                } else {
                    Exception e = exchange.getError();
                    if (e == null) {
                        e = new JBIException("Unkown error");
                    }
                    throw e;
                }
            } finally {
                // unlock the file
                unlockAsyncFile(data.file);
                // release ftp client
                returnClient(data.ftp);
            }
        } else {
            // strange, we don't know this exchange
            logger.debug("Received unknown exchange. Will be ignored...");
        }
    }

    /**
     * unlock the file
     *
     * @param file      the file to unlock
     */
    private void unlockAsyncFile(String file) {
        // finally remove the file from the open exchanges list
        Lock lock = lockManager.getLock(file);
        if (lock != null) {
            try {
                lock.unlock();
            } catch (Exception ex) {
                // can't release the lock
                logger.error(ex);
            }
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.common.locks.LockManager;
import org.apache.servicemix.common.locks.impl.SimpleLockManager;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision: 468487 $
 */
public class FtpPollerEndpoint extends PollingEndpoint implements FtpEndpointType {

    private FTPClientPool clientPool;
    private FileFilter filter;  
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean changeWorkingDirectory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private ConcurrentMap<String, FtpData> openExchanges = new ConcurrentHashMap<String, FtpData>();
    private QName targetOperation;
    private URI uri;
    private boolean stateless = true;

    protected class FtpData {
        final String file;
        final FTPClient ftp;
        final InputStream in;
        public FtpData(String file, FTPClient ftp, InputStream in) {
            this.file = file;
            this.ftp = ftp;
            this.in = in;
        }
    }

    public FtpPollerEndpoint() {
    }

    public FtpPollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FtpPollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(getWorkingPath());
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (uri == null && (getClientPool() == null || getClientPool().getHost() == null)) {
            throw new DeploymentException("Property uri or clientPool.host must be configured");
        }
        if (uri != null && getClientPool() != null && getClientPool().getHost() != null) {
            throw new DeploymentException("Properties uri and clientPool.host can not be configured at the same time");
        }
        if (changeWorkingDirectory && recursive) {
            throw new DeploymentException("changeWorkingDirectory='true' can not be set when recursive='true'");
        }
    }

    public void start() throws Exception {
        if (lockManager == null) {
            lockManager = createLockManager();
        }
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
        } else {
            String str = "ftp://" + clientPool.getHost();
            if (clientPool.getPort() >= 0) {
                str += ":" + clientPool.getPort();
            }
            str += "/";
            uri = new URI(str);
        }
        super.start();
    }

    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }

    private String getWorkingPath() {
        return (uri != null && uri.getPath() != null) ? uri.getPath() : ".";
    }

    // Properties
    //-------------------------------------------------------------------------
    /**
     * @return the clientPool
     */
    public FTPClientPool getClientPool() {
        return clientPool;
    }

    /**
     * @param clientPool the clientPool to set
     */
    public void setClientPool(FTPClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public QName getTargetOperation() {
        return targetOperation;
    }

    public void setTargetOperation(QName targetOperation) {
        this.targetOperation = targetOperation;
    }

    public void setChangeWorkingDirectory(boolean changeWorkingDirectory) {
        this.changeWorkingDirectory = changeWorkingDirectory;
    }

    public boolean isStateless() {
        return stateless;
    }

    public void setStateless(boolean stateless) {
        this.stateless = stateless;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void pollFileOrDirectory(String fileOrDirectory) throws Exception {
        FTPClient ftp = borrowClient();
        try {
            logger.debug("Polling directory " + fileOrDirectory);
            pollFileOrDirectory(ftp, fileOrDirectory, isRecursive());
        } finally {
            returnClient(ftp);
        }
    }

    protected void pollFileOrDirectory(FTPClient ftp, String fileOrDirectory, boolean processDir) throws Exception {
        FTPFile[] files = listFiles(ftp, fileOrDirectory);
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (".".equals(name) || "..".equals(name)) {
                continue; // ignore "." and ".."
            }
            String file = fileOrDirectory + "/" + name;
            // This is a file, process it
            if (!files[i].isDirectory()) {
                if (getFilter() == null || getFilter().accept(new File(file))) {
                    pollFile(file); // process the file
                }
                // Only process directories if processDir is true
            } else if (processDir) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Polling directory " + file);
                }
                pollFileOrDirectory(ftp, file, isRecursive());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping directory " + file);
                }
            }
        }
    }

    private FTPFile[] listFiles(FTPClient ftp, String directory) throws IOException {
        if (changeWorkingDirectory) {
            ftp.changeWorkingDirectory(directory);
            return ftp.listFiles();
        } else {
            return ftp.listFiles(directory);
        }
    }

    protected void pollFile(final String file) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + file + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                final Lock lock = lockManager.getLock(file);
                if (lock.tryLock()) {
                    processFileNow(file);
                }
            }
        });
    }

    protected void processFileNow(String file) {
        FTPClient ftp = null;
        try {
            ftp = borrowClient();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + file);
            }
            if (listFiles(ftp, file).length > 0) {
                // Process the file. If processing fails, an exception should be thrown.
                processFile(ftp, file);
                ftp = null;
            } else {
                //avoid processing files that have been deleted on the server
                logger.debug("Skipping " + file + ": the file no longer exists on the server");
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + file + ". Reason: " + e, e);
        } finally {
            if (ftp != null) {
                returnClient(ftp);
            }
        }
    }

    protected void processFile(FTPClient ftp, String file) throws Exception {
        InputStream in = ftp.retrieveFileStream(file);
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);

        if (getTargetOperation() != null) { 
            exchange.setOperation(getTargetOperation()); 
        }

        marshaler.readMessage(exchange, message, in, file);
        if (stateless) {
            exchange.setProperty(FtpData.class.getName(), new FtpData(file, ftp, in));
        } else {
            this.openExchanges.put(exchange.getExchangeId(), new FtpData(file, ftp, in));
        }
        send(exchange);
    }

    public String getLocationURI() {
        return uri.toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        FtpData data;
        if (stateless) {
            data = (FtpData) exchange.getProperty(FtpData.class.getName());
        } else {
            data = this.openExchanges.remove(exchange.getExchangeId());
        }
        // check for done or error
        if (data != null) {
            logger.debug("Releasing " + data.file);
            try {
                // Close ftp related stuff
                data.in.close();
                data.ftp.completePendingCommand();
                // check for state
                if (exchange.getStatus() == ExchangeStatus.DONE) {
                    if (isDeleteFile()) {
                        if (!data.ftp.deleteFile(data.file)) {
                            throw new IOException("Could not delete file " + data.file);
                        }
                    }
                } else {
                    Exception e = exchange.getError();
                    if (e == null) {
                        e = new JBIException("Unkown error");
                    }
                    throw e;
                }
            } finally {
                // unlock the file
                unlockAsyncFile(data.file);
                // release ftp client
                returnClient(data.ftp);
            }
        } else {
            // strange, we don't know this exchange
            logger.debug("Received unknown exchange. Will be ignored...");
        }
    }

    /**
     * unlock the file
     *
     * @param file      the file to unlock
     */
    private void unlockAsyncFile(String file) {
        // finally remove the file from the open exchanges list
        Lock lock = lockManager.getLock(file);
        if (lock != null) {
            try {
                lock.unlock();
            } catch (Exception ex) {
                // can't release the lock
                logger.error(ex);
            }
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

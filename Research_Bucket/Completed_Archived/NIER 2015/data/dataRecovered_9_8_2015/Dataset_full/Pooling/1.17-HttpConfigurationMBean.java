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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getJettyClientThreadPoolSize();

    void setJettyClientThreadPoolSize(int jettyClientThreadPoolSize);
    
    boolean isJettyClientPerProvider();
    
    void setJettyClientPerProvider(boolean jettyClientPerProvider);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getJettyClientThreadPoolSize();

    void setJettyClientThreadPoolSize(int jettyClientThreadPoolSize);
    
    boolean isJettyClientPerProvider();
    
    void setJettyClientPerProvider(boolean jettyClientPerProvider);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);

    public int getConnectorMaxIdleTime();

    public void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    public int getConsumerProcessorSuspendTime();

    public void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    public int getRetryCount();

    public void setRetryCount(int retryCount);

    public String getProxyHost();

    public void setProxyHost(String name);

    public int getProxyPort();

    public void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);

    public int getConnectorMaxIdleTime();

    public void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    public int getConsumerProcessorSuspendTime();

    public void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    public int getRetryCount();

    public void setRetryCount(int retryCount);

    public String getProxyHost();

    public void setProxyHost(String name);

    public int getProxyPort();

    public void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);
    
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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);
    
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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);

    public int getConnectorMaxIdleTime();

    public void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    public int getConsumerProcessorSuspendTime();

    public void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    public int getRetryCount();

    public void setRetryCount(int retryCount);

    public String getProxyHost();

    public void setProxyHost(String name);

    public int getProxyPort();

    public void setProxyPort(int name);

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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);

    public int getConnectorMaxIdleTime();

    public void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    public int getConsumerProcessorSuspendTime();

    public void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    public int getRetryCount();

    public void setRetryCount(int retryCount);

    public String getProxyHost();

    public void setProxyHost(String name);

    public int getProxyPort();

    public void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    public boolean isStreamingEnabled();

    public void setStreamingEnabled(boolean streamingEnabled);

    public String getJettyConnectorClassName();

    public void setJettyConnectorClassName(String jettyConnectorClassName);

    public int getJettyThreadPoolSize();

    public void setJettyThreadPoolSize(int jettyThreadPoolSize);
    
    public int getMaxConnectionsPerHost();
    
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost);
    
    public int getMaxTotalConnections();
    
    public void setMaxTotalConnections(int maxTotalConnections);
    
    public String getKeystoreManagerName();
    
    public void setKeystoreManagerName(String name);
    
    public String getAuthenticationServiceName();
    
    public void setAuthenticationServiceName(String name);
    
    public boolean isJettyManagement();
    
    public void setJettyManagement(boolean jettyManagement);
    
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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getJettyClientThreadPoolSize();

    void setJettyClientThreadPoolSize(int jettyClientThreadPoolSize);
    
    boolean isJettyClientPerProvider();
    
    void setJettyClientPerProvider(boolean jettyClientPerProvider);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getJettyClientThreadPoolSize();

    void setJettyClientThreadPoolSize(int jettyClientThreadPoolSize);
    
    boolean isJettyClientPerProvider();
    
    void setJettyClientPerProvider(boolean jettyClientPerProvider);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

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

public interface HttpConfigurationMBean {

    boolean isStreamingEnabled();

    void setStreamingEnabled(boolean streamingEnabled);

    String getJettyConnectorClassName();

    void setJettyConnectorClassName(String jettyConnectorClassName);

    int getJettyThreadPoolSize();

    void setJettyThreadPoolSize(int jettyThreadPoolSize);

    int getJettyClientThreadPoolSize();

    void setJettyClientThreadPoolSize(int jettyClientThreadPoolSize);
    
    boolean isJettyClientPerProvider();
    
    void setJettyClientPerProvider(boolean jettyClientPerProvider);

    int getMaxConnectionsPerHost();

    void setMaxConnectionsPerHost(int maxConnectionsPerHost);

    int getMaxTotalConnections();

    void setMaxTotalConnections(int maxTotalConnections);

    String getKeystoreManagerName();

    void setKeystoreManagerName(String name);

    String getAuthenticationServiceName();

    void setAuthenticationServiceName(String name);

    boolean isJettyManagement();

    void setJettyManagement(boolean jettyManagement);

    int getConnectorMaxIdleTime();

    void setConnectorMaxIdleTime(int connectorMaxIdleTime);

    int getConsumerProcessorSuspendTime();

    void setConsumerProcessorSuspendTime(int consumerProcessorSuspendTime);

    int getRetryCount();

    void setRetryCount(int retryCount);

    String getProxyHost();

    void setProxyHost(String name);

    int getProxyPort();

    void setProxyPort(int name);

    boolean isWantHeadersFromHttpIntoExchange();

    void setWantHeadersFromHttpIntoExchange(boolean wantHeadersFromHttpIntoExchange);

}

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
package org.apache.servicemix.nmr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.w3c.dom.Document;

import org.apache.servicemix.nmr.api.Endpoint;
import org.apache.servicemix.nmr.api.EndpointRegistry;
import org.apache.servicemix.nmr.api.NMR;
import org.apache.servicemix.nmr.api.Reference;
import org.apache.servicemix.nmr.api.ServiceMixException;
import org.apache.servicemix.nmr.api.event.EndpointListener;
import org.apache.servicemix.nmr.api.internal.InternalEndpoint;
import org.apache.servicemix.nmr.api.service.ServiceRegistry;
import org.apache.servicemix.nmr.core.util.Filter;
import org.apache.servicemix.nmr.core.util.MapToDictionary;

/**
 * Implementation of {@link EndpointRegistry} interface that defines
 * methods to register, undergister and query endpoints.
 *
 * @version $Revision: $
 * @since 4.0
 */
public class EndpointRegistryImpl implements EndpointRegistry {

    private NMR nmr;
    private ConcurrentMap<Endpoint, InternalEndpoint> endpoints = new ConcurrentHashMap<Endpoint, InternalEndpoint>();
    private Map<InternalEndpoint, Endpoint> wrappers = new ConcurrentHashMap<InternalEndpoint, Endpoint>();
    private Map<DynamicReferenceImpl, Boolean> references = new WeakHashMap<DynamicReferenceImpl, Boolean>();
    private ServiceRegistry<InternalEndpoint> registry;

    public EndpointRegistryImpl() {
    }

    public EndpointRegistryImpl(NMR nmr) {
        this.nmr = nmr;
    }

    public NMR getNmr() {
        return this.nmr;
    }

    public void setNmr(NMR nmr) {
        this.nmr = nmr;
    }

    public ServiceRegistry<InternalEndpoint> getRegistry() {
        return registry;
    }

    public void setRegistry(ServiceRegistry<InternalEndpoint> registry) {
        this.registry = registry;
    }

    public void init() {
        if (nmr == null) {
            throw new NullPointerException("nmr must be not null");
        }
        if (registry == null) {
            registry = new ServiceRegistryImpl<InternalEndpoint>();
        }
    }

    /**
     * Register the given endpoint in the registry.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     * Upon registration, a {@link org.apache.servicemix.nmr.api.Channel} will be injected onto the Endpoint using
     * the {@link org.apache.servicemix.nmr.api.Endpoint#setChannel(org.apache.servicemix.nmr.api.Channel)} method.
     *
     * @param endpoint   the endpoint to register
     * @param properties the metadata associated with this endpoint
     * @see org.apache.servicemix.nmr.api.Endpoint
     */
    public void register(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpointWrapper wrapper = new InternalEndpointWrapper(endpoint);
        if (endpoints.putIfAbsent(endpoint, wrapper) == null) {
            Executor executor = Executors.newCachedThreadPool();
            ChannelImpl channel = new ChannelImpl(wrapper, executor, nmr);
            wrapper.setChannel(channel);
            wrappers.put(wrapper, endpoint);
            registry.register(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointRegistered(wrapper);
            }
            for (DynamicReferenceImpl ref : references.keySet()) {
                ref.setDirty();
            }
        }
    }

    /**
     * Unregister a previously register enpoint.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     *
     * @param endpoint the endpoint to unregister
     */
    public void unregister(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
            if (wrapper != null) {
                endpoint = wrappers.remove(wrapper);
                if (endpoint != null) {
                    endpoints.remove(endpoint);
                }
            }
        } else {
            wrapper = endpoints.remove(endpoint);
            if (wrapper != null) {
                wrappers.remove(wrapper);
            }
        }
        if (wrapper != null) {
            registry.unregister(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointUnregistered(wrapper);
            }
        }
        for (DynamicReferenceImpl ref : references.keySet()) {
            ref.setDirty();
        }
    }

    /**
     * Get a set of registered services.
     *
     * @return the registered services
     */
    @SuppressWarnings("unchecked")
    public Set<Endpoint> getServices() {
        return (Set<Endpoint>) (Set) registry.getServices();
    }

    /**
     * Retrieve the metadata associated to a registered service.
     *
     * @param endpoint the service for which to retrieve metadata
     * @return the metadata associated with the=is endpoint
     */
    public Map<String, ?> getProperties(Endpoint endpoint) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
        } else {
            wrapper = endpoints.get(endpoint);
        }
        return registry.getProperties(wrapper);
    }


    /**
     * Query the registry for a list of registered endpoints.
     *
     * @param properties filtering data
     * @return the list of endpoints matching the filters
     */
    @SuppressWarnings("unchecked")
    public List<Endpoint> query(Map<String, ?> properties) {
        return (List<Endpoint>) (List) internalQuery(properties);
    }

    /**
     * From a given amount of metadata which could include interface name, service name
     * policy data and so forth, choose an available endpoint reference to use
     * for invocations.
     * <p/>
     * This could return actual endpoints, or a dynamic proxy to a number of endpoints
     */
    public Reference lookup(final Map<String, ?> properties) {
        DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
            public boolean match(InternalEndpoint endpoint) {
                Map<String, ?> epProps = registry.getProperties(endpoint);
                for (Map.Entry<String, ?> name : properties.entrySet()) {
                    if (!name.getValue().equals(epProps.get(name.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
        });
        this.references.put(ref, true);
        return ref;
    }

    /**
     * This methods creates a Reference from its xml representation.
     *
     * @param xml the xml document describing this reference
     * @return a new Reference
     * @see org.apache.servicemix.nmr.api.Reference#toXml()
     */
    public synchronized Reference lookup(Document xml) {
        // TODO: implement
        return null;
    }

    /**
     * Creates a Reference that select endpoints that match the
     * given LDAP filter.
     *
     * @param filter a LDAP filter used to find matching endpoints
     * @return a new Reference that uses the given filter
     */
    public Reference lookup(String filter) {
        try {
            try {
                final org.osgi.framework.Filter flt = org.osgi.framework.FrameworkUtil.createFilter(filter);
                DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
                    public boolean match(InternalEndpoint endpoint) {
                        Map<String, ?> props = EndpointRegistryImpl.this.getProperties(endpoint);
                        return flt.match(new MapToDictionary(props));
                    }
                });
                this.references.put(ref, true);
                return ref;
            } catch (org.osgi.framework.InvalidSyntaxException e) {
                throw new ServiceMixException("Invalid filter syntax: " + e.getMessage());
            }
        } catch (NoClassDefFoundError e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected List<InternalEndpoint> internalQuery(Map<String, ?> properties) {
        List<InternalEndpoint> endpoints = new ArrayList<InternalEndpoint>();
        if (properties == null) {
            endpoints.addAll(registry.getServices());
        } else {
            for (InternalEndpoint e : registry.getServices()) {
                boolean match = true;
                for (String name : properties.keySet()) {
                    if (!properties.get(name).equals(registry.getProperties(e).get(name))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    endpoints.add(e);
                }
            }
        }
        return endpoints;
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
package org.apache.servicemix.nmr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.w3c.dom.Document;

import org.apache.servicemix.nmr.api.Endpoint;
import org.apache.servicemix.nmr.api.EndpointRegistry;
import org.apache.servicemix.nmr.api.NMR;
import org.apache.servicemix.nmr.api.Reference;
import org.apache.servicemix.nmr.api.ServiceMixException;
import org.apache.servicemix.nmr.api.event.EndpointListener;
import org.apache.servicemix.nmr.api.internal.InternalEndpoint;
import org.apache.servicemix.nmr.api.service.ServiceRegistry;
import org.apache.servicemix.nmr.core.util.Filter;
import org.apache.servicemix.nmr.core.util.MapToDictionary;

/**
 * Implementation of {@link EndpointRegistry} interface that defines
 * methods to register, undergister and query endpoints.
 *
 * @version $Revision: $
 * @since 4.0
 */
public class EndpointRegistryImpl implements EndpointRegistry {

    private NMR nmr;
    private ConcurrentMap<Endpoint, InternalEndpoint> endpoints = new ConcurrentHashMap<Endpoint, InternalEndpoint>();
    private Map<InternalEndpoint, Endpoint> wrappers = new ConcurrentHashMap<InternalEndpoint, Endpoint>();
    private Map<DynamicReferenceImpl, Boolean> references = new WeakHashMap<DynamicReferenceImpl, Boolean>();
    private ServiceRegistry<InternalEndpoint> registry;

    public EndpointRegistryImpl() {
    }

    public EndpointRegistryImpl(NMR nmr) {
        this.nmr = nmr;
    }

    public NMR getNmr() {
        return this.nmr;
    }

    public void setNmr(NMR nmr) {
        this.nmr = nmr;
    }

    public ServiceRegistry<InternalEndpoint> getRegistry() {
        return registry;
    }

    public void setRegistry(ServiceRegistry<InternalEndpoint> registry) {
        this.registry = registry;
    }

    public void init() {
        if (nmr == null) {
            throw new NullPointerException("nmr must be not null");
        }
        if (registry == null) {
            registry = new ServiceRegistryImpl<InternalEndpoint>();
        }
    }

    /**
     * Register the given endpoint in the registry.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     * Upon registration, a {@link org.apache.servicemix.nmr.api.Channel} will be injected onto the Endpoint using
     * the {@link org.apache.servicemix.nmr.api.Endpoint#setChannel(org.apache.servicemix.nmr.api.Channel)} method.
     *
     * @param endpoint   the endpoint to register
     * @param properties the metadata associated with this endpoint
     * @see org.apache.servicemix.nmr.api.Endpoint
     */
    public void register(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpointWrapper wrapper = new InternalEndpointWrapper(endpoint);
        if (endpoints.putIfAbsent(endpoint, wrapper) == null) {
            Executor executor = Executors.newCachedThreadPool();
            ChannelImpl channel = new ChannelImpl(wrapper, executor, nmr);
            wrapper.setChannel(channel);
            wrappers.put(wrapper, endpoint);
            registry.register(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointRegistered(wrapper);
            }
            for (DynamicReferenceImpl ref : references.keySet()) {
                ref.setDirty();
            }
        }
    }

    /**
     * Unregister a previously register enpoint.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     *
     * @param endpoint the endpoint to unregister
     */
    public void unregister(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
            endpoint = wrappers.remove(wrapper);
            endpoints.remove(endpoint);
        } else {
            wrapper = endpoints.remove(endpoint);
            wrappers.remove(wrapper);
        }
        registry.unregister(wrapper, properties);
        for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
            listener.endpointUnregistered(wrapper);
        }
        for (DynamicReferenceImpl ref : references.keySet()) {
            ref.setDirty();
        }
    }

    /**
     * Get a set of registered services.
     *
     * @return the registered services
     */
    @SuppressWarnings("unchecked")
    public Set<Endpoint> getServices() {
        return (Set<Endpoint>) (Set) registry.getServices();
    }

    /**
     * Retrieve the metadata associated to a registered service.
     *
     * @param endpoint the service for which to retrieve metadata
     * @return the metadata associated with the=is endpoint
     */
    public Map<String, ?> getProperties(Endpoint endpoint) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
        } else {
            wrapper = endpoints.get(endpoint);
        }
        return registry.getProperties(wrapper);
    }


    /**
     * Query the registry for a list of registered endpoints.
     *
     * @param properties filtering data
     * @return the list of endpoints matching the filters
     */
    @SuppressWarnings("unchecked")
    public List<Endpoint> query(Map<String, ?> properties) {
        return (List<Endpoint>) (List) internalQuery(properties);
    }

    /**
     * From a given amount of metadata which could include interface name, service name
     * policy data and so forth, choose an available endpoint reference to use
     * for invocations.
     * <p/>
     * This could return actual endpoints, or a dynamic proxy to a number of endpoints
     */
    public Reference lookup(final Map<String, ?> properties) {
        DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
            public boolean match(InternalEndpoint endpoint) {
                Map<String, ?> epProps = registry.getProperties(endpoint);
                for (Map.Entry<String, ?> name : properties.entrySet()) {
                    if (!name.getValue().equals(epProps.get(name.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
        });
        this.references.put(ref, true);
        return ref;
    }

    /**
     * This methods creates a Reference from its xml representation.
     *
     * @param xml the xml document describing this reference
     * @return a new Reference
     * @see org.apache.servicemix.nmr.api.Reference#toXml()
     */
    public synchronized Reference lookup(Document xml) {
        // TODO: implement
        return null;
    }

    /**
     * Creates a Reference that select endpoints that match the
     * given LDAP filter.
     *
     * @param filter a LDAP filter used to find matching endpoints
     * @return a new Reference that uses the given filter
     */
    public Reference lookup(String filter) {
        try {
            try {
                final org.osgi.framework.Filter flt = org.osgi.framework.FrameworkUtil.createFilter(filter);
                DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
                    public boolean match(InternalEndpoint endpoint) {
                        Map<String, ?> props = EndpointRegistryImpl.this.getProperties(endpoint);
                        return flt.match(new MapToDictionary(props));
                    }
                });
                this.references.put(ref, true);
                return ref;
            } catch (org.osgi.framework.InvalidSyntaxException e) {
                throw new ServiceMixException("Invalid filter syntax: " + e.getMessage());
            }
        } catch (NoClassDefFoundError e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected List<InternalEndpoint> internalQuery(Map<String, ?> properties) {
        List<InternalEndpoint> endpoints = new ArrayList<InternalEndpoint>();
        if (properties == null) {
            endpoints.addAll(registry.getServices());
        } else {
            for (InternalEndpoint e : registry.getServices()) {
                boolean match = true;
                for (String name : properties.keySet()) {
                    if (!properties.get(name).equals(registry.getProperties(e).get(name))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    endpoints.add(e);
                }
            }
        }
        return endpoints;
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
package org.apache.servicemix.nmr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.w3c.dom.Document;

import org.apache.servicemix.nmr.api.Endpoint;
import org.apache.servicemix.nmr.api.EndpointRegistry;
import org.apache.servicemix.nmr.api.NMR;
import org.apache.servicemix.nmr.api.Reference;
import org.apache.servicemix.nmr.api.ServiceMixException;
import org.apache.servicemix.nmr.api.event.EndpointListener;
import org.apache.servicemix.nmr.api.internal.InternalEndpoint;
import org.apache.servicemix.nmr.api.service.ServiceRegistry;
import org.apache.servicemix.nmr.core.util.Filter;
import org.apache.servicemix.nmr.core.util.MapToDictionary;

/**
 * Implementation of {@link EndpointRegistry} interface that defines
 * methods to register, undergister and query endpoints.
 *
 * @version $Revision: $
 * @since 4.0
 */
public class EndpointRegistryImpl implements EndpointRegistry {

    private NMR nmr;
    private ConcurrentMap<Endpoint, InternalEndpoint> endpoints = new ConcurrentHashMap<Endpoint, InternalEndpoint>();
    private Map<InternalEndpoint, Endpoint> wrappers = new ConcurrentHashMap<InternalEndpoint, Endpoint>();
    private Map<DynamicReferenceImpl, Boolean> references = new WeakHashMap<DynamicReferenceImpl, Boolean>();
    private ServiceRegistry<InternalEndpoint> registry;

    public EndpointRegistryImpl() {
    }

    public EndpointRegistryImpl(NMR nmr) {
        this.nmr = nmr;
    }

    public NMR getNmr() {
        return this.nmr;
    }

    public void setNmr(NMR nmr) {
        this.nmr = nmr;
    }

    public ServiceRegistry<InternalEndpoint> getRegistry() {
        return registry;
    }

    public void setRegistry(ServiceRegistry<InternalEndpoint> registry) {
        this.registry = registry;
    }

    public void init() {
        if (nmr == null) {
            throw new NullPointerException("nmr must be not null");
        }
        if (registry == null) {
            registry = new ServiceRegistryImpl<InternalEndpoint>();
        }
    }

    /**
     * Register the given endpoint in the registry.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     * Upon registration, a {@link org.apache.servicemix.nmr.api.Channel} will be injected onto the Endpoint using
     * the {@link org.apache.servicemix.nmr.api.Endpoint#setChannel(org.apache.servicemix.nmr.api.Channel)} method.
     *
     * @param endpoint   the endpoint to register
     * @param properties the metadata associated with this endpoint
     * @see org.apache.servicemix.nmr.api.Endpoint
     */
    public void register(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpointWrapper wrapper = new InternalEndpointWrapper(endpoint);
        if (endpoints.putIfAbsent(endpoint, wrapper) == null) {
            Executor executor = Executors.newCachedThreadPool();
            ChannelImpl channel = new ChannelImpl(wrapper, executor, nmr);
            wrapper.setChannel(channel);
            wrappers.put(wrapper, endpoint);
            registry.register(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointRegistered(wrapper);
            }
            for (DynamicReferenceImpl ref : references.keySet()) {
                ref.setDirty();
            }
        }
    }

    /**
     * Unregister a previously register enpoint.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     *
     * @param endpoint the endpoint to unregister
     */
    public void unregister(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
            if (wrapper != null) {
                endpoint = wrappers.remove(wrapper);
                if (endpoint != null) {
                    endpoints.remove(endpoint);
                }
            }
        } else {
            wrapper = endpoints.remove(endpoint);
            if (wrapper != null) {
                wrappers.remove(wrapper);
            }
        }
        if (wrapper != null) {
            registry.unregister(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointUnregistered(wrapper);
            }
        }
        for (DynamicReferenceImpl ref : references.keySet()) {
            ref.setDirty();
        }
    }

    /**
     * Get a set of registered services.
     *
     * @return the registered services
     */
    @SuppressWarnings("unchecked")
    public Set<Endpoint> getServices() {
        return (Set<Endpoint>) (Set) registry.getServices();
    }

    /**
     * Retrieve the metadata associated to a registered service.
     *
     * @param endpoint the service for which to retrieve metadata
     * @return the metadata associated with the=is endpoint
     */
    public Map<String, ?> getProperties(Endpoint endpoint) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
        } else {
            wrapper = endpoints.get(endpoint);
        }
        return registry.getProperties(wrapper);
    }


    /**
     * Query the registry for a list of registered endpoints.
     *
     * @param properties filtering data
     * @return the list of endpoints matching the filters
     */
    @SuppressWarnings("unchecked")
    public List<Endpoint> query(Map<String, ?> properties) {
        return (List<Endpoint>) (List) internalQuery(properties);
    }

    /**
     * From a given amount of metadata which could include interface name, service name
     * policy data and so forth, choose an available endpoint reference to use
     * for invocations.
     * <p/>
     * This could return actual endpoints, or a dynamic proxy to a number of endpoints
     */
    public Reference lookup(final Map<String, ?> properties) {
        DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
            public boolean match(InternalEndpoint endpoint) {
                Map<String, ?> epProps = registry.getProperties(endpoint);
                for (Map.Entry<String, ?> name : properties.entrySet()) {
                    if (!name.getValue().equals(epProps.get(name.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
            public String toString() {
                return properties.toString();
            }
        });
        this.references.put(ref, true);
        return ref;
    }

    /**
     * This methods creates a Reference from its xml representation.
     *
     * @param xml the xml document describing this reference
     * @return a new Reference
     * @see org.apache.servicemix.nmr.api.Reference#toXml()
     */
    public synchronized Reference lookup(Document xml) {
        // TODO: implement
        return null;
    }

    /**
     * Creates a Reference that select endpoints that match the
     * given LDAP filter.
     *
     * @param filter a LDAP filter used to find matching endpoints
     * @return a new Reference that uses the given filter
     */
    public Reference lookup(final String filter) {
        try {
            try {
                final org.osgi.framework.Filter flt = org.osgi.framework.FrameworkUtil.createFilter(filter);
                DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
                    public boolean match(InternalEndpoint endpoint) {
                        Map<String, ?> props = EndpointRegistryImpl.this.getProperties(endpoint);
                        return flt.match(new MapToDictionary(props));
                    }
                    public String toString() {
                        return filter;
                    }
                });
                this.references.put(ref, true);
                return ref;
            } catch (org.osgi.framework.InvalidSyntaxException e) {
                throw new ServiceMixException("Invalid filter syntax: " + e.getMessage());
            }
        } catch (NoClassDefFoundError e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected List<InternalEndpoint> internalQuery(Map<String, ?> properties) {
        List<InternalEndpoint> endpoints = new ArrayList<InternalEndpoint>();
        if (properties == null) {
            endpoints.addAll(registry.getServices());
        } else {
            for (InternalEndpoint e : registry.getServices()) {
                boolean match = true;
                for (String name : properties.keySet()) {
                    if (!properties.get(name).equals(registry.getProperties(e).get(name))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    endpoints.add(e);
                }
            }
        }
        return endpoints;
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
package org.apache.servicemix.nmr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.w3c.dom.Document;

import org.apache.servicemix.nmr.api.Endpoint;
import org.apache.servicemix.nmr.api.EndpointRegistry;
import org.apache.servicemix.nmr.api.NMR;
import org.apache.servicemix.nmr.api.Reference;
import org.apache.servicemix.nmr.api.ServiceMixException;
import org.apache.servicemix.nmr.api.event.EndpointListener;
import org.apache.servicemix.nmr.api.internal.InternalEndpoint;
import org.apache.servicemix.nmr.api.service.ServiceRegistry;
import org.apache.servicemix.nmr.core.util.Filter;
import org.apache.servicemix.nmr.core.util.MapToDictionary;

/**
 * Implementation of {@link EndpointRegistry} interface that defines
 * methods to register, undergister and query endpoints.
 *
 * @version $Revision: $
 * @since 4.0
 */
public class EndpointRegistryImpl implements EndpointRegistry {

    private NMR nmr;
    private ConcurrentMap<Endpoint, InternalEndpoint> endpoints = new ConcurrentHashMap<Endpoint, InternalEndpoint>();
    private Map<InternalEndpoint, Endpoint> wrappers = new ConcurrentHashMap<InternalEndpoint, Endpoint>();
    private Map<DynamicReferenceImpl, Boolean> references = new WeakHashMap<DynamicReferenceImpl, Boolean>();
    private ServiceRegistry<InternalEndpoint> registry;

    public EndpointRegistryImpl() {
    }

    public EndpointRegistryImpl(NMR nmr) {
        this.nmr = nmr;
    }

    public NMR getNmr() {
        return this.nmr;
    }

    public void setNmr(NMR nmr) {
        this.nmr = nmr;
    }

    public ServiceRegistry<InternalEndpoint> getRegistry() {
        return registry;
    }

    public void setRegistry(ServiceRegistry<InternalEndpoint> registry) {
        this.registry = registry;
    }

    public void init() {
        if (nmr == null) {
            throw new NullPointerException("nmr must be not null");
        }
        if (registry == null) {
            registry = new ServiceRegistryImpl<InternalEndpoint>();
        }
    }

    /**
     * Register the given endpoint in the registry.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     * Upon registration, a {@link org.apache.servicemix.nmr.api.Channel} will be injected onto the Endpoint using
     * the {@link org.apache.servicemix.nmr.api.Endpoint#setChannel(org.apache.servicemix.nmr.api.Channel)} method.
     *
     * @param endpoint   the endpoint to register
     * @param properties the metadata associated with this endpoint
     * @see org.apache.servicemix.nmr.api.Endpoint
     */
    public void register(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpointWrapper wrapper = new InternalEndpointWrapper(endpoint);
        if (endpoints.putIfAbsent(endpoint, wrapper) == null) {
            Executor executor = Executors.newCachedThreadPool();
            ChannelImpl channel = new ChannelImpl(wrapper, executor, nmr);
            wrapper.setChannel(channel);
            wrappers.put(wrapper, endpoint);
            registry.register(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointRegistered(wrapper);
            }
            for (DynamicReferenceImpl ref : references.keySet()) {
                ref.setDirty();
            }
        }
    }

    /**
     * Unregister a previously register enpoint.
     * In an OSGi world, this would be performed automatically by a ServiceTracker.
     *
     * @param endpoint the endpoint to unregister
     */
    public void unregister(Endpoint endpoint, Map<String, ?> properties) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
            if (wrapper != null) {
                endpoint = wrappers.remove(wrapper);
                if (endpoint != null) {
                    endpoints.remove(endpoint);
                }
            }
        } else {
            wrapper = endpoints.remove(endpoint);
            if (wrapper != null) {
                wrappers.remove(wrapper);
            }
        }
        if (wrapper != null) {
            registry.unregister(wrapper, properties);
            for (EndpointListener listener : nmr.getListenerRegistry().getListeners(EndpointListener.class)) {
                listener.endpointUnregistered(wrapper);
            }
        }
        for (DynamicReferenceImpl ref : references.keySet()) {
            ref.setDirty();
        }
    }

    /**
     * Get a set of registered services.
     *
     * @return the registered services
     */
    @SuppressWarnings("unchecked")
    public Set<Endpoint> getServices() {
        return (Set<Endpoint>) (Set) registry.getServices();
    }

    /**
     * Retrieve the metadata associated to a registered service.
     *
     * @param endpoint the service for which to retrieve metadata
     * @return the metadata associated with the=is endpoint
     */
    public Map<String, ?> getProperties(Endpoint endpoint) {
        InternalEndpoint wrapper;
        if (endpoint instanceof InternalEndpoint) {
            wrapper = (InternalEndpoint) endpoint;
        } else {
            wrapper = endpoints.get(endpoint);
        }
        return registry.getProperties(wrapper);
    }


    /**
     * Query the registry for a list of registered endpoints.
     *
     * @param properties filtering data
     * @return the list of endpoints matching the filters
     */
    @SuppressWarnings("unchecked")
    public List<Endpoint> query(Map<String, ?> properties) {
        return (List<Endpoint>) (List) internalQuery(properties);
    }

    /**
     * From a given amount of metadata which could include interface name, service name
     * policy data and so forth, choose an available endpoint reference to use
     * for invocations.
     * <p/>
     * This could return actual endpoints, or a dynamic proxy to a number of endpoints
     */
    public Reference lookup(final Map<String, ?> properties) {
        DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
            public boolean match(InternalEndpoint endpoint) {
                Map<String, ?> epProps = registry.getProperties(endpoint);
                for (Map.Entry<String, ?> name : properties.entrySet()) {
                    if (!name.getValue().equals(epProps.get(name.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
            public String toString() {
                return properties.toString();
            }
        });
        this.references.put(ref, true);
        return ref;
    }

    /**
     * This methods creates a Reference from its xml representation.
     *
     * @param xml the xml document describing this reference
     * @return a new Reference
     * @see org.apache.servicemix.nmr.api.Reference#toXml()
     */
    public synchronized Reference lookup(Document xml) {
        // TODO: implement
        return null;
    }

    /**
     * Creates a Reference that select endpoints that match the
     * given LDAP filter.
     *
     * @param filter a LDAP filter used to find matching endpoints
     * @return a new Reference that uses the given filter
     */
    public Reference lookup(final String filter) {
        try {
            try {
                final org.osgi.framework.Filter flt = org.osgi.framework.FrameworkUtil.createFilter(filter);
                DynamicReferenceImpl ref = new DynamicReferenceImpl(this, new Filter<InternalEndpoint>() {
                    public boolean match(InternalEndpoint endpoint) {
                        Map<String, ?> props = EndpointRegistryImpl.this.getProperties(endpoint);
                        return flt.match(new MapToDictionary(props));
                    }
                    public String toString() {
                        return filter;
                    }
                });
                this.references.put(ref, true);
                return ref;
            } catch (org.osgi.framework.InvalidSyntaxException e) {
                throw new ServiceMixException("Invalid filter syntax: " + e.getMessage());
            }
        } catch (NoClassDefFoundError e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected List<InternalEndpoint> internalQuery(Map<String, ?> properties) {
        List<InternalEndpoint> endpoints = new ArrayList<InternalEndpoint>();
        if (properties == null) {
            endpoints.addAll(registry.getServices());
        } else {
            for (InternalEndpoint e : registry.getServices()) {
                boolean match = true;
                for (String name : properties.keySet()) {
                    if (!properties.get(name).equals(registry.getProperties(e).get(name))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    endpoints.add(e);
                }
            }
        }
        return endpoints;
    }

}

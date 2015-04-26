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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.management;

import java.io.IOException;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;

public class JaasAuthenticator implements JMXAuthenticator {

    private String realm;

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got "
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        final String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        try {
            LoginContext loginContext = new LoginContext(realm, new CallbackHandler() {
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (int i = 0; i < callbacks.length; i++) {
                        if (callbacks[i] instanceof NameCallback) {
                            ((NameCallback) callbacks[i]).setName(params[0]);
                        } else if (callbacks[i] instanceof PasswordCallback) {
                            ((PasswordCallback) callbacks[i]).setPassword((params[1].toCharArray()));
                        } else {
                            throw new UnsupportedCallbackException(callbacks[i]);
                        }
                    }
                }
            });
            loginContext.login();
            return loginContext.getSubject();
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (!(credentials instanceof String[])) {
            throw new IllegalArgumentException("Expected String[2], got " 
                            + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
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
package org.apache.servicemix.jbi.jmx;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.servicemix.jbi.security.auth.AuthenticationService;
import org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="jmxJaasAuthenticator"
 */
public class JaasAuthenticator implements JMXAuthenticator {

    private String domain = "servicemix-domain";
    private AuthenticationService authenticationService = new JAASAuthenticationService();

    /**
     * The authentication service can be used to customize the authentication
     * mechanism used by this authenticator.  It defaults to a 
     * JAASAuthenticationService which delegates calls to the JAAS layer.
     * 
     * @return the authenticationService
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @return the JAAS domain to use for authentication
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the JAAS domain to use for authentication
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /* (non-Javadoc)
     * @see javax.management.remote.JMXAuthenticator#authenticate(java.lang.Object)
     */
    public Subject authenticate(Object credentials) throws SecurityException {
        if (credentials instanceof String[] == false) {
            throw new IllegalArgumentException("Expected String[2], got " + (credentials != null ? credentials.getClass().getName() : null));
        }
        String[] params = (String[]) credentials;
        if (params.length != 2) {
            throw new IllegalArgumentException("Expected String[2] but length was " + params.length);
        }
        Subject subject = new Subject();
        try {
            authenticationService.authenticate(subject, domain, params[0], params[1]);
        } catch (LoginException e) {
            throw new SecurityException("Authentication failed", e);
        } catch (Exception e) {
            throw new SecurityException("Error occured while authenticating", e);
        }
        return subject;
    }

}

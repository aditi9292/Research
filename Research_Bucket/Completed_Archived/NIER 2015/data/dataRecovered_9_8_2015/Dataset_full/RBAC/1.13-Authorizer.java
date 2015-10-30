/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.lenya.ac;

import org.apache.avalon.framework.component.Component;
import org.apache.cocoon.environment.Request;

/**
 * An Authorizer checks if an Identity is authorized to invoke a certain request.
 * @version $Id: Authorizer.java 473861 2006-11-12 03:51:14Z gregor $
 */
public interface Authorizer extends Component {
    
    /**
     * The Avalon role.
     */
    String ROLE = Authorizer.class.getName();

    /**
     * Authorizes an identity at a URL.
     * @param request The request.
     * @return <code>true</code> if the identity is authorized, <code>false</code> otherwise.
     * @throws AccessControlException when something went wrong.
     */
    boolean authorize(Request request)
        throws AccessControlException;

}
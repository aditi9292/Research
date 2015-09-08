/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.triplesec.admin.dao;


import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.naming.directory.ModificationItem;

import org.apache.directory.triplesec.admin.DataAccessException;
import org.apache.directory.triplesec.admin.Profile;


public interface ProfileDao
{
    Iterator profileIterator( String applicationName ) 
        throws DataAccessException;

    Iterator profileIterator( String applicationName, String user ) 
        throws DataAccessException;

    Profile load( String applicationName, String id ) 
        throws DataAccessException;

    Profile add( String applicationName, String id, String user, String description, 
        Set grants, Set denials, Set roles ) throws DataAccessException;

    Profile rename( String newId, Profile archetype ) throws DataAccessException;

    Profile modify( String creatorsName, Date createTimestamp, String applicationName, String id, 
        String user, String description, Set grants, Set denials, Set roles, boolean disabled, ModificationItem[] mods ) 
        throws DataAccessException;

    void delete( String name, String id ) throws DataAccessException;
}

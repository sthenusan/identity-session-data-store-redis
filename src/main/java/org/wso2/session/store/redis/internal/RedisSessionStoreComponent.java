/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.session.store.redis.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.identity.application.authentication.framework.store.SessionDataStore;
import org.wso2.carbon.identity.core.util.IdentityCoreInitializedEvent;
import org.wso2.session.store.redis.RedisSessionDataStore;

/**
 * OSGI service component for the application management UI.
 */
@Component(
        name = "org.wso2.session.store.redis",
        immediate = true
)
public class RedisSessionStoreComponent {

    private static Log log = LogFactory.getLog(RedisSessionStoreComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        context.getBundleContext().registerService(SessionDataStore.class.getName(), new RedisSessionDataStore(), null);

        if (log.isDebugEnabled()) {
            log.debug("RedisSessionDataStore is activated successfully.");
        }

    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("RedisSessionDataStore is deactivated successfully.");
        }
    }

    /**
     * Reference IdentityCoreInitializedEvent service to guarantee that this component will wait until identity core
     * is started
     */
    @Reference(
            name = "identityCoreInitializedEventService",
            service = IdentityCoreInitializedEvent.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIdentityCoreInitializedEventService"
    )
    protected void setIdentityCoreInitializedEventService(IdentityCoreInitializedEvent identityCoreInitializedEvent) {

        if (log.isDebugEnabled()) {
            log.debug("RedisSessionDataStore is deactivated successfully.");
        }
    }

    /**
     * Reference IdentityCoreInitializedEvent service to guarantee that this component will wait until identity core
     * is started
     *
     * @param identityCoreInitializedEvent
     */
    protected void unsetIdentityCoreInitializedEventService(IdentityCoreInitializedEvent identityCoreInitializedEvent) {

    }

}

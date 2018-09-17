/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.receiver.register.provider;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.receiver.register.module.RegisterModule;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v5.*;

/**
 * @author peng-yongsheng
 */
public class RegisterModuleProvider extends ModuleProvider {

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return RegisterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return null;
    }

    @Override public void prepare() {
    }

    @Override public void start() {
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(CoreModule.NAME).getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(new ApplicationRegisterHandler(getManager()));
        grpcHandlerRegister.addHandler(new InstanceDiscoveryServiceHandler(getManager()));
        grpcHandlerRegister.addHandler(new ServiceNameDiscoveryHandler(getManager()));
        grpcHandlerRegister.addHandler(new NetworkAddressRegisterServiceHandler(getManager()));
    }

    @Override public void notifyAfterCompleted() {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}

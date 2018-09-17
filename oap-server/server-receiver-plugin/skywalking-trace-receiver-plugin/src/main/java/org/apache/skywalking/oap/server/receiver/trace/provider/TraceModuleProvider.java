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

package org.apache.skywalking.oap.server.receiver.trace.provider;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.TraceSegmentServiceHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.endpoint.MultiScopesSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.service.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.SegmentStandardizationWorker;

/**
 * @author peng-yongsheng
 */
public class TraceModuleProvider extends ModuleProvider {

    private final TraceServiceModuleConfig moduleConfig;

    public TraceModuleProvider() {
        this.moduleConfig = new TraceServiceModuleConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return TraceModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override public void prepare() {
    }

    @Override public void start() throws ModuleStartException {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        listenerManager.add(new MultiScopesSpanListener.Factory());
        listenerManager.add(new ServiceComponentSpanListener.Factory());
        listenerManager.add(new ServiceMappingSpanListener.Factory());

        GRPCHandlerRegister grpcHandlerRegister = getManager().find(CoreModule.NAME).getService(GRPCHandlerRegister.class);
        try {
            SegmentParse segmentParse = new SegmentParse(getManager(), listenerManager);
            grpcHandlerRegister.addHandler(new TraceSegmentServiceHandler(segmentParse));

            SegmentStandardizationWorker standardizationWorker = new SegmentStandardizationWorker(segmentParse, moduleConfig.getBufferPath(), moduleConfig.getBufferOffsetMaxFileSize(), moduleConfig.getBufferDataMaxFileSize(), moduleConfig.isBufferFileCleanWhenRestart());
            segmentParse.setStandardizationWorker(standardizationWorker);
        } catch (IOException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}

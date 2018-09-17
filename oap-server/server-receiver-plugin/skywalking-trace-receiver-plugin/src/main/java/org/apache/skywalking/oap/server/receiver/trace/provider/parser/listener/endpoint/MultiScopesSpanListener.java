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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.endpoint;

import java.util.*;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 *
 * Notice, in here, there are following concepts match
 *
 *       v5        |   v6
 *
 * 1. Application == Service
 * 2. Server == Service Instance
 * 3. Service == Endpoint
 *
 * @author peng-yongsheng, wusheng
 */
public class MultiScopesSpanListener implements EntrySpanListener, ExitSpanListener {

    private static final Logger logger = LoggerFactory.getLogger(MultiScopesSpanListener.class);

    private final SourceReceiver sourceReceiver;
    private final ServiceInstanceInventoryCache instanceInventoryCache;
    private final ServiceInventoryCache serviceInventoryCache;
    private final EndpointInventoryCache endpointInventoryCache;

    private final List<SourceBuilder> entrySourceBuilders;
    private final List<SourceBuilder> exitSourceBuilders;
    private SpanDecorator entrySpanDecorator;
    private long minuteTimeBucket;

    private MultiScopesSpanListener(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).getService(SourceReceiver.class);
        this.entrySourceBuilders = new LinkedList<>();
        this.exitSourceBuilders = new LinkedList<>();
        this.instanceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
        this.endpointInventoryCache = moduleManager.find(CoreModule.NAME).getService(EndpointInventoryCache.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        this.minuteTimeBucket = segmentCoreInfo.getMinuteTimeBucket();

        if (spanDecorator.getRefsCount() > 0) {
            for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                ReferenceDecorator reference = spanDecorator.getRefs(i);
                SourceBuilder sourceBuilder = new SourceBuilder();
                sourceBuilder.setSourceEndpointId(reference.getParentServiceId());

                if (spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
                    int serviceIdByPeerId = serviceInventoryCache.getServiceId(reference.getNetworkAddressId());
                    int instanceIdByPeerId = instanceInventoryCache.getServiceInstanceId(serviceIdByPeerId, reference.getNetworkAddressId());
                    sourceBuilder.setSourceServiceInstanceId(instanceIdByPeerId);
                    sourceBuilder.setSourceServiceId(serviceIdByPeerId);
                } else {
                    sourceBuilder.setSourceServiceInstanceId(reference.getParentApplicationInstanceId());
                    sourceBuilder.setSourceServiceId(instanceInventoryCache.get(reference.getParentApplicationInstanceId()).getServiceId());
                }
                sourceBuilder.setDestEndpointId(spanDecorator.getOperationNameId());
                sourceBuilder.setDestServiceInstanceId(segmentCoreInfo.getApplicationInstanceId());
                sourceBuilder.setDestServiceId(segmentCoreInfo.getApplicationId());
                sourceBuilder.setDetectPoint(DetectPoint.SERVER);
                setPublicAttrs(sourceBuilder, spanDecorator);
                entrySourceBuilders.add(sourceBuilder);
            }
        } else {
            SourceBuilder sourceBuilder = new SourceBuilder();
            sourceBuilder.setSourceEndpointId(Const.NONE_ENDPOINT_ID);
            sourceBuilder.setSourceServiceInstanceId(Const.NONE_INSTANCE_ID);
            sourceBuilder.setSourceServiceId(Const.NONE_SERVICE_ID);
            sourceBuilder.setDestEndpointId(spanDecorator.getOperationNameId());
            sourceBuilder.setDestServiceInstanceId(segmentCoreInfo.getApplicationInstanceId());
            sourceBuilder.setDestServiceId(segmentCoreInfo.getApplicationId());
            sourceBuilder.setDetectPoint(DetectPoint.SERVER);

            setPublicAttrs(sourceBuilder, spanDecorator);
            entrySourceBuilders.add(sourceBuilder);
        }
        this.entrySpanDecorator = spanDecorator;
    }

    @Override public void parseExit(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (this.minuteTimeBucket == 0) {
            this.minuteTimeBucket = segmentCoreInfo.getMinuteTimeBucket();
        }

        SourceBuilder sourceBuilder = new SourceBuilder();

        int peerId = spanDecorator.getPeerId();
        int destServiceId = serviceInventoryCache.getServiceId(peerId);
        int destInstanceId = instanceInventoryCache.getServiceInstanceId(destServiceId, peerId);

        sourceBuilder.setSourceEndpointId(Const.NONE_ENDPOINT_ID);
        sourceBuilder.setSourceServiceInstanceId(segmentCoreInfo.getApplicationInstanceId());
        sourceBuilder.setSourceServiceId(segmentCoreInfo.getApplicationId());
        sourceBuilder.setDestEndpointId(spanDecorator.getOperationNameId());
        sourceBuilder.setDestServiceInstanceId(destInstanceId);
        sourceBuilder.setDestServiceId(destServiceId);
        sourceBuilder.setDetectPoint(DetectPoint.CLIENT);
        setPublicAttrs(sourceBuilder, spanDecorator);
        exitSourceBuilders.add(sourceBuilder);
    }

    private void setPublicAttrs(SourceBuilder sourceBuilder, SpanDecorator spanDecorator) {
        long latency = spanDecorator.getEndTime() - spanDecorator.getStartTime();
        sourceBuilder.setLatency((int)latency);
        sourceBuilder.setResponseCode(Const.NONE);
        sourceBuilder.setStatus(spanDecorator.getIsError());

        switch (spanDecorator.getSpanLayer()) {
            case Http:
                sourceBuilder.setType(RequestType.HTTP);
                break;
            case Database:
                sourceBuilder.setType(RequestType.DATABASE);
                break;
            default:
                sourceBuilder.setType(RequestType.RPC);
                break;
        }

        sourceBuilder.setSourceServiceName(serviceInventoryCache.get(sourceBuilder.getSourceServiceId()).getName());
        sourceBuilder.setSourceServiceInstanceName(instanceInventoryCache.get(sourceBuilder.getSourceServiceInstanceId()).getName());
        sourceBuilder.setSourceEndpointName(endpointInventoryCache.get(sourceBuilder.getSourceEndpointId()).getName());
        sourceBuilder.setDestServiceName(serviceInventoryCache.get(sourceBuilder.getDestServiceId()).getName());
        sourceBuilder.setDestServiceInstanceName(instanceInventoryCache.get(sourceBuilder.getDestServiceInstanceId()).getName());
        sourceBuilder.setDestEndpointName(endpointInventoryCache.get(sourceBuilder.getDestEndpointId()).getName());
    }

    @Override public void build() {
        entrySourceBuilders.forEach(entrySourceBuilder -> {
            entrySourceBuilder.setTimeBucket(minuteTimeBucket);
            sourceReceiver.receive(entrySourceBuilder.toAll());
            sourceReceiver.receive(entrySourceBuilder.toService());
            sourceReceiver.receive(entrySourceBuilder.toServiceInstance());
            sourceReceiver.receive(entrySourceBuilder.toEndpoint());
            sourceReceiver.receive(entrySourceBuilder.toServiceRelation());
            sourceReceiver.receive(entrySourceBuilder.toServiceInstanceRelation());
            sourceReceiver.receive(entrySourceBuilder.toEndpointRelation());
        });

        exitSourceBuilders.forEach(exitSourceBuilder -> {
            if (nonNull(entrySpanDecorator)) {
                exitSourceBuilder.setSourceEndpointId(entrySpanDecorator.getOperationNameId());
            } else {
                exitSourceBuilder.setSourceEndpointId(Const.NONE_ENDPOINT_ID);
            }
            exitSourceBuilder.setSourceEndpointName(endpointInventoryCache.get(exitSourceBuilder.getSourceEndpointId()).getName());

            exitSourceBuilder.setTimeBucket(minuteTimeBucket);
            sourceReceiver.receive(exitSourceBuilder.toService());
            sourceReceiver.receive(exitSourceBuilder.toServiceInstance());
            sourceReceiver.receive(exitSourceBuilder.toEndpoint());
            sourceReceiver.receive(exitSourceBuilder.toServiceRelation());
            sourceReceiver.receive(exitSourceBuilder.toServiceInstanceRelation());
            sourceReceiver.receive(exitSourceBuilder.toEndpointRelation());
        });
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager) {
            return new MultiScopesSpanListener(moduleManager);
        }
    }
}

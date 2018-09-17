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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.data.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class IndicatorPersistentWorker extends AbstractWorker<Indicator> {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorPersistentWorker.class);

    private final String modelName;
    private final MergeDataCache<Indicator> mergeDataCache;
    private final IBatchDAO batchDAO;
    private final IIndicatorDAO indicatorDAO;
    private final int blockBatchPersistenceSize;
    private final AbstractWorker<Indicator> nextWorker;

    IndicatorPersistentWorker(int workerId, String modelName, int batchSize, ModuleManager moduleManager,
        IIndicatorDAO indicatorDAO, AbstractWorker<Indicator> nextWorker) {
        super(workerId);
        this.modelName = modelName;
        this.blockBatchPersistenceSize = batchSize;
        this.mergeDataCache = new MergeDataCache<>();
        this.batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);
        this.indicatorDAO = indicatorDAO;
        this.nextWorker = nextWorker;
    }

    public final Window<MergeDataCollection<Indicator>> getCache() {
        return mergeDataCache;
    }

    @Override public final void in(Indicator input) {
        if (getCache().currentCollectionSize() >= blockBatchPersistenceSize) {
            try {
                if (getCache().trySwitchPointer()) {
                    getCache().switchPointer();

                    List<?> collection = buildBatchCollection();
                    batchDAO.batchPersistence(collection);
                }
            } finally {
                getCache().trySwitchPointerFinally();
            }
        }
        cacheData(input);
    }

    public boolean flushAndSwitch() {
        boolean isSwitch;
        try {
            if (isSwitch = getCache().trySwitchPointer()) {
                getCache().switchPointer();
            }
        } finally {
            getCache().trySwitchPointerFinally();
        }
        return isSwitch;
    }

    public final List<?> buildBatchCollection() {
        List<?> batchCollection = new LinkedList<>();
        try {
            while (getCache().getLast().isWriting()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    logger.warn("thread wake up");
                }
            }

            if (getCache().getLast().collection() != null) {
                batchCollection = prepareBatch(getCache().getLast());
            }
        } finally {
            getCache().finishReadingLast();
        }
        return batchCollection;
    }

    private List<Object> prepareBatch(MergeDataCollection<Indicator> collection) {
        List<Object> batchCollection = new LinkedList<>();
        collection.collection().forEach((id, data) -> {
            Indicator dbData = null;
            try {
                dbData = indicatorDAO.get(modelName, data);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
            try {
                if (nonNull(dbData)) {
                    data.combine(dbData);
                    batchCollection.add(indicatorDAO.prepareBatchUpdate(modelName, data));
                } else {
                    batchCollection.add(indicatorDAO.prepareBatchInsert(modelName, data));
                }

                nextWorker.in(data);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        });

        return batchCollection;
    }

    private void cacheData(Indicator input) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(input)) {
            Indicator indicator = mergeDataCache.get(input);
            indicator.combine(input);
            indicator.calculate();
        } else {
            input.calculate();
            mergeDataCache.put(input);
        }

        mergeDataCache.finishWriting();
    }
}

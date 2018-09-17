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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.storage.model.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class StorageAnnotationListener implements AnnotationListener, IModelGetter {

    private static final Logger logger = LoggerFactory.getLogger(StorageAnnotationListener.class);

    @Getter private final List<Model> models;

    public StorageAnnotationListener() {
        this.models = new LinkedList<>();
    }

    @Override public Class<? extends Annotation> annotation() {
        return StorageEntity.class;
    }

    @Override public void notify(Class aClass) {
        logger.info("The owner class of storage annotation, class name: {}", aClass.getName());

        String modelName = StorageEntityAnnotationUtils.getModelName(aClass);

        List<ModelColumn> modelColumns = new LinkedList<>();
        retrieval(aClass, modelName, modelColumns);

        models.add(new Model(modelName, modelColumns));
    }

    private void retrieval(Class clazz, String modelName, List<ModelColumn> modelColumns) {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                modelColumns.add(new ModelColumn(new ColumnName(column.columnName(), column.columnName()), field.getType()));
                if (logger.isDebugEnabled()) {
                    logger.debug("The field named {} with the {} type", column.columnName(), field.getType());
                }
                if (column.isValue()) {
                    ValueColumnIds.INSTANCE.putIfAbsent(modelName, column.columnName(), column.function());
                }
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns);
        }
    }
}

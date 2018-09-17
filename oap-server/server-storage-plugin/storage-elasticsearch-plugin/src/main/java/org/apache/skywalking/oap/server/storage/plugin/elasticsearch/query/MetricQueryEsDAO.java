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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.query.sql.*;
import org.apache.skywalking.oap.server.core.storage.TimePyramidTableNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IMetricQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class MetricQueryEsDAO extends EsDAO implements IMetricQueryDAO {

    public MetricQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    public IntValues getValues(String indName, Step step, long startTB, long endTB, Where where, String valueCName,
        Function function) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        queryBuild(sourceBuilder, where, startTB, endTB);

        TermsAggregationBuilder entityIdAggregation = AggregationBuilders.terms(Indicator.ENTITY_ID).field(Indicator.ENTITY_ID).size(1000);
        functionAggregation(function, entityIdAggregation, valueCName);

        sourceBuilder.aggregation(entityIdAggregation);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        IntValues intValues = new IntValues();
        Terms idTerms = response.getAggregations().get(Indicator.ENTITY_ID);
        for (Terms.Bucket idBucket : idTerms.getBuckets()) {
            Terms valueTerms = idBucket.getAggregations().get(valueCName);
            for (Terms.Bucket valueBucket : valueTerms.getBuckets()) {
                KVInt value = new KVInt();
                value.setId(idBucket.getKeyAsString());
                value.setValue(valueBucket.getKeyAsNumber().intValue());
                intValues.getValues().add(value);
            }
        }
        return intValues;
    }

    private void functionAggregation(Function function, TermsAggregationBuilder parentAggBuilder, String valueCName) {
        switch (function) {
            case Avg:
                parentAggBuilder.subAggregation(AggregationBuilders.avg(valueCName).field(valueCName));
                break;
            case Sum:
                parentAggBuilder.subAggregation(AggregationBuilders.sum(valueCName).field(valueCName));
                break;
        }
    }

    @Override public IntValues getLinearIntValues(String indName, Step step, List<String> ids,
        String valueCName) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        MultiGetResponse response = getClient().multiGet(indexName, ids);

        IntValues intValues = new IntValues();
        for (MultiGetItemResponse itemResponse : response.getResponses()) {
            int value = ((Number)itemResponse.getResponse().getSource().getOrDefault(valueCName, 0)).intValue();

            KVInt kvInt = new KVInt();
            kvInt.setId(itemResponse.getId());
            kvInt.setValue(value);
            intValues.getValues().add(kvInt);
        }
        return intValues;
    }

    @Override public Thermodynamic getThermodynamic(String indName, Step step, List<String> ids,
        String valueCName) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        MultiGetResponse response = getClient().multiGet(indexName, ids);

        Thermodynamic thermodynamic = new Thermodynamic();
        for (MultiGetItemResponse itemResponse : response.getResponses()) {
            int axisYStep = ((Number)itemResponse.getResponse().getSource().get(ThermodynamicIndicator.STEP)).intValue();
            thermodynamic.setAxisYStep(axisYStep);
            int numOfSteps = ((Number)itemResponse.getResponse().getSource().get(ThermodynamicIndicator.NUM_OF_STEPS)).intValue();

            String value = (String)itemResponse.getResponse().getSource().get(ThermodynamicIndicator.DETAIL_GROUP);
            IntKeyLongValueArray intKeyLongValues = new IntKeyLongValueArray();
            intKeyLongValues.toObject(value);

            List<Long> axisYValues = new ArrayList<>();
            for (int i = 0; i < numOfSteps; i++) {
                axisYValues.add(0L);
            }

            for (IntKeyLongValue intKeyLongValue : intKeyLongValues) {
                axisYValues.set(intKeyLongValue.getKey(), intKeyLongValue.getValue());
            }

            thermodynamic.getNodes().add(axisYValues);
        }
        return thermodynamic;
    }
}

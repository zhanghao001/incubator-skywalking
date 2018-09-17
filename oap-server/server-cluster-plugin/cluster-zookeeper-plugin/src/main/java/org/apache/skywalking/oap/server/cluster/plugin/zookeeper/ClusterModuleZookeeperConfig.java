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

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.util.StringUtils;

/**
 * @author peng-yongsheng
 */
class ClusterModuleZookeeperConfig extends ModuleConfig {
    private String hostPort;
    private int baseSleepTimeMs;
    private int maxRetries;

    public String getHostPort() {
        return StringUtils.isNotEmpty(hostPort) ? hostPort : "localhost:2181";
    }

    public void setHostPort(String hostPort) {
        this.hostPort = hostPort;
    }

    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs > 0 ? baseSleepTimeMs : 1000;
    }

    public void setBaseSleepTimeMs(int baseSleepTimeMs) {
        this.baseSleepTimeMs = baseSleepTimeMs;
    }

    public int getMaxRetries() {
        return maxRetries > 0 ? maxRetries : 3;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}

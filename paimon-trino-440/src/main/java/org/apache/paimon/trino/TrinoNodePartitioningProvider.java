/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.trino;

import com.google.inject.Inject;
import io.trino.spi.connector.BucketFunction;
import io.trino.spi.connector.ConnectorNodePartitioningProvider;
import io.trino.spi.connector.ConnectorPartitioningHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.type.Type;

import java.util.List;

/** Trino {@link ConnectorNodePartitioningProvider}. */
public class TrinoNodePartitioningProvider implements ConnectorNodePartitioningProvider {

    @Inject
    public TrinoNodePartitioningProvider() {}

    @Override
    public BucketFunction getBucketFunction(
            ConnectorTransactionHandle transactionHandle,
            ConnectorSession session,
            ConnectorPartitioningHandle partitioningHandle,
            List<Type> partitionChannelTypes,
            int workerCount) {
        // todo support dynamic bucket tables
        TrinoPartitioningHandle trinoPartitioningHandle =
                (TrinoPartitioningHandle) partitioningHandle;
        switch (trinoPartitioningHandle.getBucketMode()) {
            case FIXED:
                return new FixedBucketTableShuffleFunction(
                        partitionChannelTypes, trinoPartitioningHandle, workerCount);
            case UNAWARE:
                return new UnawareTableShuffleFunction(
                        partitionChannelTypes, trinoPartitioningHandle, workerCount);
            default:
                throw new UnsupportedOperationException(
                        "Unsupported bucket mode: " + trinoPartitioningHandle.getBucketMode());
        }
    }
}

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

import org.apache.paimon.schema.TableSchema;
import org.apache.paimon.table.BucketMode;
import org.apache.paimon.utils.InstantiationUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ConnectorPartitioningHandle;

import java.io.IOException;
import java.util.Arrays;

/** Trino {@link ConnectorPartitioningHandle}. */
public class TrinoPartitioningHandle implements ConnectorPartitioningHandle {

    private final byte[] schema;
    private final BucketMode bucketMode;

    @JsonCreator
    public TrinoPartitioningHandle(
            @JsonProperty("schema") byte[] schema,
            @JsonProperty("bucketMode") BucketMode bucketMode) {
        this.schema = schema;
        this.bucketMode = bucketMode;
    }

    @JsonProperty
    public byte[] getSchema() {
        return schema;
    }

    @JsonProperty
    public BucketMode getBucketMode() {
        return bucketMode;
    }

    public TableSchema getOriginalSchema() {
        try {
            return InstantiationUtil.deserializeObject(this.schema, getClass().getClassLoader());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TrinoPartitioningHandle that = (TrinoPartitioningHandle) o;
        return Arrays.equals(schema, that.getSchema());
    }
}

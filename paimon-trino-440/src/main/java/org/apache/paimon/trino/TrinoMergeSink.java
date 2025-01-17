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

import org.apache.paimon.types.RowKind;

import io.airlift.slice.Slice;
import io.trino.spi.Page;
import io.trino.spi.block.Block;
import io.trino.spi.connector.ConnectorMergeSink;
import io.trino.spi.connector.ConnectorPageSink;
import io.trino.spi.type.TinyintType;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** Trino {@link ConnectorMergeSink}. */
public class TrinoMergeSink implements ConnectorMergeSink {

    private final TrinoPageSink pageSink;
    private final int dataColumnCount;

    public TrinoMergeSink(ConnectorPageSink pageSink, int dataColumnCount) {
        this.pageSink = (TrinoPageSink) pageSink;
        this.dataColumnCount = dataColumnCount;
    }

    @Override
    public void storeMergedRows(Page page) {
        int inputChannelCount = page.getChannelCount();
        if (inputChannelCount != dataColumnCount + 2) {
            throw new IllegalArgumentException(
                    String.format(
                            "inputPage channelCount (%s) == dataColumns size (%s) + 2",
                            inputChannelCount, dataColumnCount));
        } else {
            int positionCount = page.getPositionCount();
            if (positionCount <= 0) {
                throw new IllegalArgumentException(
                        "positionCount should be > 0, but is " + positionCount);
            } else {
                Block operationBlock = page.getBlock(inputChannelCount - 2);
                int[] deletePositions = new int[positionCount];
                int[] insertPositions = new int[positionCount];
                int deletePositionCount = 0;
                int insertPositionCount = 0;

                for (int position = 0; position < positionCount; ++position) {
                    byte operation = TinyintType.TINYINT.getByte(operationBlock, position);
                    switch (operation) {
                        case 1:
                        case 4:
                            insertPositions[insertPositionCount] = position;
                            ++insertPositionCount;
                            break;
                        case 2:
                        case 5:
                            deletePositions[deletePositionCount] = position;
                            ++deletePositionCount;
                            break;
                        case 3:
                        default:
                            throw new IllegalArgumentException(
                                    "Invalid merge operation: " + operation);
                    }
                }

                Optional<Page> deletePage = Optional.empty();
                if (deletePositionCount > 0) {
                    deletePage =
                            Optional.of(
                                    page.getColumns(IntStream.range(0, dataColumnCount).toArray())
                                            .getPositions(deletePositions, 0, deletePositionCount));
                }

                Optional<Page> insertPage = Optional.empty();
                if (insertPositionCount > 0) {
                    insertPage =
                            Optional.of(
                                    page.getColumns(IntStream.range(0, dataColumnCount).toArray())
                                            .getPositions(insertPositions, 0, insertPositionCount));
                }

                deletePage.ifPresent(delete -> pageSink.writePage(delete, RowKind.DELETE));
                insertPage.ifPresent(insert -> pageSink.writePage(insert, RowKind.INSERT));
            }
        }
    }

    @Override
    public CompletableFuture<Collection<Slice>> finish() {
        return pageSink.finish();
    }
}

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

import org.apache.paimon.data.BinaryString;
import org.apache.paimon.data.Decimal;
import org.apache.paimon.data.InternalArray;
import org.apache.paimon.data.InternalMap;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.data.Timestamp;
import org.apache.paimon.table.sink.BatchTableWrite;
import org.apache.paimon.table.sink.CommitMessage;
import org.apache.paimon.table.sink.CommitMessageSerializer;
import org.apache.paimon.types.RowKind;

import io.airlift.slice.Slice;
import io.trino.spi.Page;
import io.trino.spi.connector.ConnectorPageSink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.airlift.slice.Slices.wrappedBuffer;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.paimon.shade.guava30.com.google.common.base.Verify.verify;

/** TrinoPageSink. */
public class TrinoPageSink implements ConnectorPageSink {

    private final BatchTableWrite writer;

    public TrinoPageSink(BatchTableWrite writer) {
        this.writer = writer;
    }

    @Override
    public CompletableFuture<?> appendPage(Page page) {
        try {
            writePage(page, RowKind.INSERT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return NOT_BLOCKED;
    }

    public void writePage(Page page, RowKind rowKind) {
        try {
            for (int i = 0; i < page.getPositionCount(); i++) {
                writer.write(new TrinoRow(page.getSingleValuePage(i), rowKind));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Collection<Slice>> finish() {
        Collection<Slice> commitTasks = new ArrayList<>();
        try {
            List<CommitMessage> commitMessages = writer.prepareCommit();
            CommitMessageSerializer serializer = new CommitMessageSerializer();
            for (CommitMessage commitMessage : commitMessages) {
                commitTasks.add(wrappedBuffer(serializer.serialize(commitMessage)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return completedFuture(commitTasks);
    }

    @Override
    public void abort() {
        try {
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class TrinoRow implements InternalRow {

        private final RowKind rowKind;
        private final Page singlePage;

        public TrinoRow(Page singlePage, RowKind rowKind) {
            verify(singlePage.getPositionCount() == 1, "singlePage must have only one row");
            this.singlePage = singlePage;
            this.rowKind = rowKind;
        }

        @Override
        public int getFieldCount() {
            return singlePage.getChannelCount();
        }

        @Override
        public RowKind getRowKind() {
            return rowKind;
        }

        @Override
        public void setRowKind(RowKind rowKind) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNullAt(int i) {
            return singlePage.getBlock(i).isNull(0);
        }

        @Override
        public boolean getBoolean(int i) {
            return singlePage.getBlock(i).getByte(0, 0) != 0;
        }

        @Override
        public byte getByte(int i) {
            return singlePage.getBlock(i).getByte(0, 0);
        }

        @Override
        public short getShort(int i) {
            return singlePage.getBlock(i).getShort(0, 0);
        }

        @Override
        public int getInt(int i) {
            return singlePage.getBlock(i).getInt(0, 0);
        }

        @Override
        public long getLong(int i) {
            return singlePage.getBlock(i).getInt(0, 0);
        }

        @Override
        public float getFloat(int i) {
            return Float.intBitsToFloat(Math.toIntExact(singlePage.getBlock(i).getLong(0, 0)));
        }

        @Override
        public double getDouble(int i) {
            return Double.longBitsToDouble(singlePage.getBlock(i).getLong(0, 0));
        }

        @Override
        public BinaryString getString(int i) {
            return BinaryString.fromBytes(getBinary(i));
        }

        @Override
        public Decimal getDecimal(int i, int decimalPrecision, int decimalScale) {
            return Decimal.fromUnscaledLong(
                    singlePage.getBlock(i).getLong(0, 0), decimalPrecision, decimalScale);
        }

        @Override
        public Timestamp getTimestamp(int i, int timestampPrecision) {
            long timestampMicros = singlePage.getBlock(i).getLong(0, 0);
            return Timestamp.fromMicros(timestampMicros);
        }

        @Override
        public byte[] getBinary(int i) {
            int length = singlePage.getBlock(i).getSliceLength(0);
            return singlePage.getBlock(i).getSlice(0, 0, length).getBytes();
        }

        @Override
        public InternalArray getArray(int i) {
            // todo
            //            singlePage.getBlock(i).getChildren()
            return null;
        }

        @Override
        public InternalMap getMap(int i) {
            // todo
            return null;
        }

        @Override
        public InternalRow getRow(int i, int i1) {
            // todo
            return null;
        }
    }
}

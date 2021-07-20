/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.table.format;

import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.common.table.log.HoodieMergedLogRecordScanner;
import org.apache.hudi.config.HoodieMemoryConfig;
import org.apache.hudi.configuration.FlinkOptions;
import org.apache.hudi.hadoop.config.HoodieRealtimeConfig;
import org.apache.hudi.table.format.mor.MergeOnReadInputSplit;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.generic.IndexedRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utilities for format.
 */
public class FormatUtils {
  private FormatUtils() {
  }

  public static GenericRecord buildAvroRecordBySchema(
      IndexedRecord record,
      Schema requiredSchema,
      int[] requiredPos,
      GenericRecordBuilder recordBuilder) {
    List<Schema.Field> requiredFields = requiredSchema.getFields();
    assert (requiredFields.size() == requiredPos.length);
    Iterator<Integer> positionIterator = Arrays.stream(requiredPos).iterator();
    requiredFields.forEach(f -> recordBuilder.set(f, record.get(positionIterator.next())));
    return recordBuilder.build();
  }

  public static HoodieMergedLogRecordScanner scanLog(
      MergeOnReadInputSplit split,
      Schema logSchema,
      Configuration config) {
    FileSystem fs = FSUtils.getFs(split.getTablePath(), config);
    return HoodieMergedLogRecordScanner.newBuilder()
        .withFileSystem(fs)
        .withBasePath(split.getTablePath())
        .withLogFilePaths(split.getLogPaths().get())
        .withReaderSchema(logSchema)
        .withLatestInstantTime(split.getLatestCommit())
        .withReadBlocksLazily(config.getBoolean(HoodieRealtimeConfig.COMPACTION_LAZY_BLOCK_READ_ENABLED_PROP.key(),
            HoodieRealtimeConfig.COMPACTION_LAZY_BLOCK_READ_ENABLED_PROP.defaultValue()))
        .withReverseReader(false)
        .withBufferSize(config.getInt(HoodieMemoryConfig.MAX_DFS_STREAM_BUFFER_SIZE_PROP.key(),
            HoodieMemoryConfig.MAX_DFS_STREAM_BUFFER_SIZE_PROP.defaultValue()))
        .withMaxMemorySizeInBytes(split.getMaxCompactionMemoryInBytes())
        .withSpillableMapBasePath(config.get(HoodieMemoryConfig.SPILLABLE_MAP_BASE_PATH_PROP.key(),
            HoodieMemoryConfig.SPILLABLE_MAP_BASE_PATH_PROP.defaultValue()))
        .withInstantRange(split.getInstantRange())
        .build();
  }

  private static Boolean string2Boolean(String s) {
    return "true".equals(s.toLowerCase(Locale.ROOT));
  }

  public static org.apache.hadoop.conf.Configuration getParquetConf(
      org.apache.flink.configuration.Configuration options,
      org.apache.hadoop.conf.Configuration hadoopConf) {
    final String prefix = "parquet.";
    org.apache.hadoop.conf.Configuration copy = new org.apache.hadoop.conf.Configuration(hadoopConf);
    Map<String, String> parquetOptions = FlinkOptions.getHoodiePropertiesWithPrefix(options.toMap(), prefix);
    parquetOptions.forEach((k, v) -> copy.set(prefix + k, v));
    return copy;
  }
}

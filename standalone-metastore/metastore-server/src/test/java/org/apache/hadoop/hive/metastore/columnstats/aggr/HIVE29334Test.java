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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hadoop.hive.metastore.columnstats.aggr;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.memory.Memory;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.annotation.MetastoreUnitTest;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsData;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsObj;
import org.apache.hadoop.hive.metastore.api.Decimal;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.columnstats.ColStatsBuilder;
import org.apache.hadoop.hive.metastore.utils.MetaStoreServerUtils.ColStatsObjWithSourceInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.hadoop.hive.metastore.StatisticsTestUtils.createStatsWithInfo;

@Category(MetastoreUnitTest.class)
public class HIVE29334Test {

  private static final Table TABLE = new Table("dummy", "db", "hive", 0, 0,
      0, null, null, Collections.emptyMap(), null, null,
      TableType.MANAGED_TABLE.toString());
  private static final FieldSchema COL = new FieldSchema("col", "decimal", "");

  public static final byte[] HIST1 =
      { 5, 1, 15, 0, -56, 0, 8, 0, 84, 0, 0, 0, 0, 0, 0, 0, -56, 0, 1, 0, 116, 0, 0, 0, 0, 0, 0, 0, 102, -36, 32, 70,
          123, 60, -50, 68, 0, 0, 0, 0, 0, 0, 0, 0, -102, -103, 10, 68, -31, 122, 28, 65, -72, 30, 73, 66, -113, -126,
          30, 68, -31, -6, 95, 67, 72, -95, 31, 67, 102, -42, 20, 69, 72, -31, 58, 64, 41, 92, -57, 65, 72, -31, -102,
          64, -10, -88, 77, 67, -51, -68, 87, 68, 123, 20, -80, 66, -123, -125, -61, 68, -92, -16, -121, 66, 61, 10, -9,
          65, 72, -31, 12, 67, -102, 25, -120, 66, -41, -93, 0, 65, -51, -52, -114, 67, 102, -98, -13, 68, -113, -62,
          -32, 68, 113, -67, -123, 67, -72, -2, -24, 67, -51, -52, -62, 66, -51, 12, 59, 68, 0, -128, -67, 67, -31, -70,
          41, 68, 0, 0, -96, 65, 0, 0, 86, 67, 102, -122, 5, 68, 10, -57, 17, 68, -10, 40, -54, 66, 61, -118, -52, 67,
          0, 0, -39, 66, 72, -31, -34, 66, 31, -11, -39, 68, -72, 30, -103, 66, 102, -36, 32, 70, -82, 71, -125, 66,
          -102, -111, 57, 69, 51, -109, -81, 68, -31, 122, -8, 65, -92, -88, -88, 68, -61, 93, -16, 68, 0, -16, -71, 68,
          102, 6, -104, 68, 113, 61, 112, 67, -51, -84, 1, 68, -113, 114, -122, 68, 102, -58, -11, 67, 0, 0, -13, 66,
          -51, 92, -75, 68, -61, -75, -66, 67, -61, -123, -102, 68, -123, -21, 41, 66, 0, -128, -1, 67, 0, 64, 104, 68,
          -20, 81, -80, 64, 92, -113, -58, 65, 31, -123, 19, 65, -123, 27, 40, 68, -51, 28, 4, 68, -92, -16, 49, 67,
          -51, -52, -78, 67, 31, -107, 45, 68, -102, 25, 15, 68, 102, 70, 30, 68, -41, -93, -113, 67, -51, 12, 87, 67,
          0, 32, -67, 68, 0, -128, 65, 67, 102, 102, -74, 64, 0, 48, -105, 69, 51, 51, 7, 66, 0, -128, -99, 67, -10,
          -88, -23, 67, -20, 81, -13, 66, 102, 38, 16, 67, 102, -74, 47, 68, 82, -72, -97, 68 };
  public static final byte[] HIST2 =
      { 5, 1, 15, 0, -56, 0, 8, 0, 9, 2, 0, 0, 0, 0, 0, 0, -56, 0, 2, 0, 28, 0, 0, 0, 117, 0, 0, 0, 0, 0, 0, 0, 113, 47,
          -101, 70, 41, 68, -52, 68, 51, -13, 44, 69, 41, 68, 109, 69, 51, 83, -113, 68, 20, 14, -126, 68, -10, 40, -40,
          66, 72, -127, 27, 69, 51, 51, 92, 67, 0, -8, 122, 69, 72, 1, 9, 68, -20, 81, -44, 67, 72, 65, 92, 68, -102,
          -103, 52, 67, -113, -62, -2, 66, 102, -26, 0, 68, -10, -72, 12, 68, 113, 61, 10, 65, -61, -11, -22, 66, -31,
          94, -106, 69, 20, 46, -49, 66, 102, -26, 112, 67, -113, 2, -9, 67, 0, -128, -10, 66, -82, 71, 24, 67, 0, -110,
          -42, 69, -31, -38, -45, 67, 113, 61, 82, 65, -72, 30, 80, 68, -61, -11, 104, 64, 82, 56, 24, 68, -51, -52, 5,
          67, -102, 57, -82, 68, -92, -48, -104, 67, -51, 76, 33, 67, 72, -127, -59, 67, 51, -13, 29, 67, -82, 63, 7,
          69, -123, 107, -25, 66, -102, 45, 79, 69, 123, 20, -82, 67, -102, 25, 29, 67, -10, 40, 45, 67, -72, -98, -49,
          67, -51, -52, 4, 67, 92, -113, -114, 67, 102, 102, 86, 65, -102, 125, 126, 69, -31, 122, -79, 68, 123, 20, 10,
          67, 102, -26, 49, 67, 123, -60, 9, 68, -82, 7, -76, 67, 31, -119, -114, 69, -102, -103, -39, 66, 61, 10, -65,
          66, 82, -120, -91, 68, 92, -113, -60, 66, 102, 102, -98, 66, 82, -72, -32, 67, -51, 12, 39, 67, -92, -16, 12,
          68, 41, -36, 113, 67, -102, -103, 29, 67, 0, 0, 0, 0, -51, 28, -100, 68, 31, 101, -43, 67, 31, -91, -84, 67,
          -31, -102, -36, 67, -41, 35, -78, 68, 0, 32, -45, 67, 41, 92, -109, 65, 51, 51, 51, 65, 0, -72, 5, 69, 51,
          -77, 81, 69, -51, 44, -2, 67, -72, -98, -84, 66, -51, -52, 66, 66, 72, 5, 79, 69, 51, -109, -2, 67, 20, -82,
          71, 65, 102, 102, -107, 67, -82, 7, -6, 68, -92, 112, -32, 66, -72, 30, 67, 67, -92, -48, 2, 69, 31, -123, 94,
          67, 102, 102, 115, 66, -113, -62, -84, 68, 92, -113, -78, 66, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, -113, -62, 117, 62, 72, -31, -38, 63, -10, 40, 12, 64, -102, -103, -103, 64, 41, 92, -49, 64, -82,
          71, -47, 64, 31, -123, 51, 65, -72, 30, 65, 65, 72, -31, 90, 65, -113, -62, 117, 65, -123, -21, -123, 65, 113,
          61, -108, 65, -113, -62, -51, 65, -31, 122, -36, 65, 20, -82, -15, 65, -61, -11, -4, 65, -41, -93, 6, 66, -92,
          112, 20, 66, -20, 81, 27, 66, 102, 102, 32, 66, 102, 102, 48, 66, 10, -41, 51, 66, -41, -93, 60, 66, 102, 102,
          62, 66, 51, 51, 71, 66, -10, 40, 81, 66, 82, -72, 94, 66, -20, 81, 114, 66, 102, 102, 126, 66, -61, -11, -128,
          66, 123, 20, -120, 66, -102, -103, -112, 66, -92, 112, -111, 66, 92, 15, -106, 66, -92, 112, -96, 66, -102,
          -103, -96, 66, -10, -88, -87, 66, 102, 102, -83, 66, -51, -52, -78, 66, 92, -113, -74, 66, 41, 92, -65, 66,
          -51, -52, -63, 66, -113, -62, -59, 66, -20, -47, -52, 66, -10, -88, -36, 66, 0, -128, -32, 66, -31, 122, -24,
          66, 82, -72, -22, 66, -61, -11, -11, 66, 82, -72, -1, 66, -113, 2, 0, 67, -102, -103, 1, 67, 0, -128, 2, 67,
          82, 56, 8, 67, 10, -41, 12, 67, -51, -52, 15, 67, 20, 46, 16, 67, 10, 87, 18, 67, -31, 122, 22, 67, 20, 110,
          27, 67, 61, 10, 31, 67, -41, -93, 36, 67, 51, 51, 39, 67, -31, -70, 42, 67, -82, -57, 43, 67, -41, -93, 48,
          67, -102, -103, 52, 67, -102, -103, 62, 67, 31, 5, 63, 67, 61, 10, 66, 67, 102, 102, 68, 67, -61, -11, 70, 67,
          10, -41, 73, 67, 20, 46, 79, 67, 0, 0, 82, 67, 102, 102, 84, 67, 51, -13, 86, 67, -31, -6, 104, 67, -72, 94,
          108, 67, 0, -64, 112, 67, -123, -21, 117, 67, -61, -11, 124, 67, -41, -93, -128, 67, 102, 38, -127, 67, 113,
          125, -122, 67, -113, 66, -117, 67, 41, 124, -111, 67, 41, -4, -110, 67, 123, 20, -105, 67, 31, -91, -103, 67,
          31, -123, -100, 67, -82, -57, -100, 67, 82, -40, -93, 67, -10, -24, -92, 67, -113, -62, -86, 67, -10, 40, -85,
          67, -51, 12, -82, 67, 20, 46, -76, 67, -61, -11, -76, 67, 0, 64, -69, 67, 92, -113, -67, 67, 20, -82, -66, 67,
          123, -44, -63, 67, 0, 64, -62, 67, 102, 38, -55, 67, 31, -123, -53, 67, 51, -13, -45, 67, -31, 122, -36, 67,
          41, 92, -29, 67, -123, -21, -29, 67, 41, 92, -23, 67, 72, 97, -21, 67, -92, 112, -15, 67, -31, 122, -7, 67,
          20, -18, -7, 67, -41, -93, 1, 68, 102, 6, 3, 68, -92, -128, 7, 68, -92, -80, 7, 68, 10, -105, 11, 68, -92,
          112, 14, 68, -41, 19, 16, 68, -82, -121, 17, 68, -92, 0, 23, 68, -41, -93, 24, 68, -123, 43, 29, 68, -31, 58,
          33, 68, -20, 1, 39, 68, 102, 54, 46, 68, -102, 57, 52, 68, 41, 92, 53, 68, 92, -65, 56, 68, -123, 75, 57, 68,
          113, -35, 58, 68, -10, -56, 63, 68, 0, 96, 80, 68, 31, 69, 82, 68, 72, -127, 90, 68, -10, 104, 92, 68, 31,
          -75, 93, 68, 82, -72, 96, 68, 72, 65, 105, 68, 20, 14, 114, 68, -113, 50, 114, 68, -61, -11, 115, 68, -51, 28,
          121, 68, 92, 31, -127, 68, -123, -85, -125, 68, -82, 87, -123, 68, -102, 89, -119, 68, -51, -100, -117, 68,
          102, 94, -111, 68, -123, 67, -110, 68, -72, 54, -106, 68, -92, 96, -104, 68, 102, 14, -100, 68, -51, 12, -97,
          68, 72, -63, -93, 68, 123, -108, -89, 68, -51, 20, -74, 68, -82, 39, -70, 68, -102, -103, -47, 68, -82, 23,
          -41, 68, 82, -128, -38, 68, 51, 3, -33, 68, 31, -107, -31, 68, -113, 74, -20, 68, 82, 104, -15, 68, -41, -5,
          -14, 68, 51, 35, -10, 68, -20, 49, -10, 68, -10, 40, -5, 68, 102, -42, -2, 68, -72, -26, 5, 69, -72, -34, 9,
          69, 0, 24, 13, 69, -51, -124, 16, 69, 20, 22, 17, 69, 41, -68, 26, 69, -31, -62, 26, 69, -82, 55, 31, 69,
          -113, 66, 32, 69, -123, -89, 37, 69, 51, 3, 38, 69, -102, 25, 42, 69, -51, -20, 43, 69, 31, -91, 55, 69, -102,
          17, 61, 69, -92, -24, 64, 69, 31, 101, 71, 69, 51, 115, 72, 69, 0, -20, 75, 69, 123, -28, 76, 69, 0, -36, 81,
          69, 0, 48, 92, 69, 82, -32, 95, 69, 0, 0, 106, 69, 0, 104, 116, 69, -72, -26, 125, 69, -20, 81, -123, 69, -61,
          13, -114, 69, -20, -69, -111, 69, -51, 96, -108, 69, -20, -57, -97, 69, 51, 103, -82, 69, 0, 110, -58, 69, 41,
          -20, -58, 69, -20, -127, -39, 69, 82, -100, -39, 69, -102, -87, 15, 70, -92, -22, 75, 70 };

  @Test
  public void test1()
      throws MetaException, NoSuchAlgorithmException {
    List<String> partitions = Arrays.asList("part1", "part2");

    for(int i=0; i<10; i++) {
      System.out.println("\n\n");
    ColumnStatisticsData data1 = new ColStatsBuilder<>(Decimal.class).numNulls(1).numDVs(1)
        .kllRaw(HIST1).build();
    ColumnStatisticsData data2 = new ColStatsBuilder<>(Decimal.class).numNulls(1).numDVs(1)
        .kllRaw(HIST2).build();


    List<ColStatsObjWithSourceInfo> statsList = Arrays.asList(
        createStatsWithInfo(data1, TABLE, COL, partitions.get(0)),
        createStatsWithInfo(data2, TABLE, COL, partitions.get(1))
    );

      DecimalColumnStatsAggregator aggregator = new DecimalColumnStatsAggregator();
      ColumnStatisticsObj a1 = aggregator.aggregate(statsList, partitions, false);
      MessageDigest md = MessageDigest.getInstance("MD5");
      System.out.println(new BigInteger(md.digest(a1.getStatsData().getDecimalStats().getHistogram())));
    }

  }

  @Test
  public void test2(){
    for(int i=0; i<20; i++) {
      KllFloatsSketch start = KllFloatsSketch.newHeapInstance();

      byte[] h1 = Arrays.copyOf(HIST1, HIST1.length);
      byte[] h2 = Arrays.copyOf(HIST2, HIST2.length);

      KllFloatsSketch kll1 = KllFloatsSketch.heapify(Memory.wrap(h1));
      start.merge(kll1);

      KllFloatsSketch kll2 = KllFloatsSketch.heapify(Memory.wrap(h2));
      start.merge(kll2);

      System.out.println(new BigInteger(DigestUtils.md5(start.toByteArray())) );
    }
  }

}

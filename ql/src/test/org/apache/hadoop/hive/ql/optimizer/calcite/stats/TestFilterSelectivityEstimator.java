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
package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.quantilescommon.FloatsSketchSortedView;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.apache.datasketches.quantilescommon.QuantilesFloatsAPI;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.StatisticsTestUtils;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveRelFactories;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveTypeSystemImpl;
import org.apache.hadoop.hive.ql.optimizer.calcite.RelOptHiveTable;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveBetween;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveRelNode;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveTableScan;
import org.apache.hadoop.hive.ql.parse.CalcitePlanner;
import org.apache.hadoop.hive.ql.plan.ColStatistics;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.betweenSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.getInterpolatedRank;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.greaterThanOrEqualSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.greaterThanSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.isHistogramAvailable;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.lessThanOrEqualSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.lessThanSelectivity;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestFilterSelectivityEstimator {

  private static final float[] VALUES = { 1, 2, 2, 2, 2, 2, 2, 2, 3, 4, 5, 6, 7 };
  private static final KllFloatsSketch KLL = StatisticsTestUtils.createKll(VALUES);
  private static final float DELTA = Float.MIN_VALUE;
  private static final RexBuilder REX_BUILDER = new RexBuilder(new JavaTypeFactoryImpl(new HiveTypeSystemImpl()));
  private static final RelDataTypeFactory TYPE_FACTORY = REX_BUILDER.getTypeFactory();
  private static RelOptCluster relOptCluster;
  private static RexNode intMinus1;
  private static RexNode int0;
  private static RexNode int1;
  private static RexNode int2;
  private static RexNode int3;
  private static RexNode int4;
  private static RexNode int5;
  private static RexNode int7;
  private static RexNode int8;
  private static RexNode int10;
  private static RexNode int11;
  private static RelDataType tableType;
  private static RexNode inputRef0;
  private static RexNode boolFalse;
  private static RexNode boolTrue;
  private static ColStatistics stats;

  @Mock
  private RelOptSchema schemaMock;
  @Mock
  private RelOptHiveTable tableMock;
  @Mock
  private RelMetadataQuery mq;

  private HiveTableScan tableScan;
  private RelNode scan;

  @BeforeClass
  public static void beforeClass() {
    RelDataType integerType = TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER);
    intMinus1 = REX_BUILDER.makeLiteral(-1, integerType, true);
    int0 = REX_BUILDER.makeLiteral(0, integerType, true);
    int1 = REX_BUILDER.makeLiteral(1, integerType, true);
    int2 = REX_BUILDER.makeLiteral(2, integerType, true);
    int3 = REX_BUILDER.makeLiteral(3, integerType, true);
    int4 = REX_BUILDER.makeLiteral(4, integerType, true);
    int5 = REX_BUILDER.makeLiteral(5, integerType, true);
    int7 = REX_BUILDER.makeLiteral(7, integerType, true);
    int8 = REX_BUILDER.makeLiteral(8, integerType, true);
    int10 = REX_BUILDER.makeLiteral(10, integerType, true);
    int11 = REX_BUILDER.makeLiteral(11, integerType, true);
    boolFalse = REX_BUILDER.makeLiteral(false, TYPE_FACTORY.createSqlType(SqlTypeName.BOOLEAN), true);
    boolTrue = REX_BUILDER.makeLiteral(true, TYPE_FACTORY.createSqlType(SqlTypeName.BOOLEAN), true);
    tableType = TYPE_FACTORY.createStructType(ImmutableList.of(integerType), ImmutableList.of("f1"));

    RelOptPlanner planner = CalcitePlanner.createPlanner(new HiveConf());
    relOptCluster = RelOptCluster.create(planner, REX_BUILDER);

    stats = new ColStatistics();
    stats.setHistogram(KLL.toByteArray());
  }

  @Before
  public void before() {
    doReturn(tableType).when(tableMock).getRowType();
    doReturn((double) VALUES.length).when(tableMock).getRowCount();

    RelBuilder relBuilder = HiveRelFactories.HIVE_BUILDER.create(relOptCluster, schemaMock);
    tableScan = new HiveTableScan(relOptCluster, relOptCluster.traitSetOf(HiveRelNode.CONVENTION),
        tableMock, "table", null, false, false);
    scan = relBuilder.push(tableScan).build();
    inputRef0 = REX_BUILDER.makeInputRef(scan, 0);
  }

  @Test
  public void testIsHistogramAvailableWhenAvailable() {
    ColStatistics colStatistics = new ColStatistics();
    colStatistics.setHistogram(KLL.toByteArray());
    Assert.assertTrue(isHistogramAvailable(colStatistics));
  }

  @Test
  public void testIsHistogramAvailableWhenNullStatistics() {
    Assert.assertFalse(isHistogramAvailable(null));
  }

  @Test
  public void testIsHistogramAvailableWhenNullHistogram() {
    Assert.assertFalse(isHistogramAvailable(new ColStatistics()));
  }

  @Test
  public void testIsHistogramAvailableWhenEmptyArray() {
    ColStatistics colStatistics = new ColStatistics();
    colStatistics.setHistogram(new byte[0]);
    Assert.assertFalse(isHistogramAvailable(colStatistics));
  }

  @Test
  public void testLessThanSelectivity() {
    Assert.assertEquals(0.6153846153846154, lessThanSelectivity(KLL, 3), DELTA);
  }

  @Test
  public void testLessThanSelectivityWhenLowerThanMin() {
    Assert.assertEquals(0, lessThanSelectivity(KLL, 0), DELTA);
  }

  @Test
  public void testLessThanSelectivityWhenHigherThanMax() {
    Assert.assertEquals(1, lessThanSelectivity(KLL, 10), DELTA);
  }

  @Test
  public void testLessThanOrEqualSelectivity() {
    Assert.assertEquals(0.6923076923076923, lessThanOrEqualSelectivity(KLL, 3), DELTA);
  }

  @Test
  public void testLessThanOrEqualSelectivityWhenLowerThanMin() {
    Assert.assertEquals(0, lessThanOrEqualSelectivity(KLL, 0), DELTA);
  }

  @Test
  public void testLessThanOrEqualSelectivityWhenHigherThanMax() {
    Assert.assertEquals(1, lessThanOrEqualSelectivity(KLL, 10), DELTA);
  }

  @Test
  public void testGreaterThanSelectivity() {
    Assert.assertEquals(0.3076923076923077, greaterThanSelectivity(KLL, 3), DELTA);
  }

  @Test
  public void testGreaterThanSelectivityWhenLowerThanMin() {
    Assert.assertEquals(1, greaterThanSelectivity(KLL, 0), DELTA);
  }

  @Test
  public void testGreaterThanSelectivityWhenHigherThanMax() {
    Assert.assertEquals(0, greaterThanSelectivity(KLL, 10), DELTA);
  }

  @Test
  public void testGreaterThanOrEqualSelectivity() {
    Assert.assertEquals(0.3846153846153846, greaterThanOrEqualSelectivity(KLL, 3), DELTA);
  }

  @Test
  public void testGreaterThanOrEqualSelectivityWhenLowerThanMin() {
    Assert.assertEquals(1, greaterThanOrEqualSelectivity(KLL, 0), DELTA);
  }

  @Test
  public void testGreaterThanOrEqualSelectivityWhenHigherThanMax() {
    Assert.assertEquals(0, greaterThanOrEqualSelectivity(KLL, 10), DELTA);
  }

  @Test
  public void testBetweenSelectivity() {
    Assert.assertEquals(0.6923076923076923, betweenSelectivity(KLL, 1, 3), DELTA);
  }

  @Test
  public void testBetweenSelectivityFromMinToMax() {
    Assert.assertEquals(1, betweenSelectivity(KLL, 1, 7), DELTA);
  }

  @Test
  public void testBetweenSelectivityFromLowerThanMinToHigherThanMax() {
    Assert.assertEquals(1, betweenSelectivity(KLL, 0, 8), DELTA);
  }

  @Test
  public void testBetweenSelectivityLeftLowerThanMin() {
    Assert.assertEquals(0.6923076923076923, betweenSelectivity(KLL, 0, 3), DELTA);
  }

  @Test
  public void testBetweenSelectivityRightLowerThanMin() {
    Assert.assertEquals(0, betweenSelectivity(KLL, -1, 0), DELTA);
  }

  @Test
  public void testBetweenSelectivityLeftHigherThanMax() {
    Assert.assertEquals(0, betweenSelectivity(KLL, 10, 11), DELTA);
  }

  @Test
  public void testBetweenSelectivityLeftLowerThanRight() {
    Assert.assertEquals(0, betweenSelectivity(KLL, 4, 2), DELTA);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBetweenSelectivityLeftEqualsRight_KO() {
    betweenSelectivity(KLL, 2, 2);
  }

  @Test
  public void testComputeRangePredicateSelectivityWhenNoStats() {
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    // defaults to 1/3 in the absence of stats
    Assert.assertEquals(0.3333333333333333, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThan() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.6153846153846154, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanWhenLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN, inputRef0, int0);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanWhenHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN, inputRef0, int10);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanOrEqual() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.6923076923076923, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanOrEqualWhenLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef0, int0);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanOrEqualWhenHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef0, int10);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThan() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.3076923076923077, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanWhenLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN, inputRef0, int0);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanWhenHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN, inputRef0, int10);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanOrEqual() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.38461538461538464, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanOrEqualWhenLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef0, int0);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanOrEqualWhenHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef0, int10);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetween() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int1, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.6923076923076923, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenFromMinToMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int1, int7);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenFromLowerThanMinToHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int0, int8);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenLeftLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.6923076923076923, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenRightLowerThanMin() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, intMinus1, int0);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenLeftHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int10, int11);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenLeftLowerThanRight() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int4, int2);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenLeftEqualsRight() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    doReturn(10.0).when(mq).getDistinctRowCount(scan, ImmutableBitSet.of(0), REX_BUILDER.makeLiteral(true));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int3, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    // this is what FilterSelectivityEstimator returns for a generic "function" based on NDV values, in this case 1 / 10
    Assert.assertEquals(0.1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityNotBetween() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, inputRef0, int3, int5);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.7692307692307693, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityNotBetweenLowerThanMinHigherThanMax() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, inputRef0, int0, int10);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityNotBetweenRightLowerThanLeft() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, inputRef0, int5, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityNotBetweenLeftEqualsRight() {
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, inputRef0, int3, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(1, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.2, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityGreaterThanOrEqualWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.25, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.4, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityLessThanOrEqualWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef0, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.45, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, inputRef0, int1, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.45, estimator.estimateSelectivity(filter), DELTA);
  }

  @Test
  public void testComputeRangePredicateSelectivityNotBetweenWithNULLS() {
    doReturn((double) 20).when(tableMock).getRowCount();
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, inputRef0, int1, int3);
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(0.55, estimator.estimateSelectivity(filter), DELTA);
  }

  public static String fmt(double val) {
    return String.format("%.3f", val);
  }

  public static void main(String[] args) {
    {
      KllFloatsSketch kll = KllFloatsSketch.newHeapInstance(8);
      for (int i = 0; i < 10; i++) {
        kll.update(i);
        kll.update(i);
      }
      System.out.println(Arrays.toString(kll.getSortedView().getQuantiles()));
      System.out.println(Arrays.toString(kll.getSortedView().getCumulativeWeights()));
      System.out.println(kll.getRank(4.0f, QuantileSearchCriteria.EXCLUSIVE));
      System.out.println(kll.getRank(4.01f, QuantileSearchCriteria.EXCLUSIVE));
      System.out.println(kll.getRank(4.5f, QuantileSearchCriteria.EXCLUSIVE));
      System.out.println(kll.getRank(4.99f, QuantileSearchCriteria.EXCLUSIVE));
      System.out.println(kll.getRank(5.0f, QuantileSearchCriteria.EXCLUSIVE));
      //if (true)
      //  return;
    }

    KllFloatsSketch t1 = getLinearKllFloatSketch1to100();
    FloatsSketchSortedView sv;

    sv = t1.getSortedView();

    System.out.println(Arrays.toString(sv.getCumulativeWeights()));
    System.out.println(sv.getCumulativeWeights().length);
    System.out.println(Arrays.toString(sv.getQuantiles()));
    System.out.println(sv.getQuantiles().length);

    long n = sv.getN();
    for (int i = -5; i <= 110; i += 1) {
      check(t1, i, n);
    }

    //System.out.println("quantiles");
    //for (int i = 0; i < sv.getQuantiles().length; i++) {
    //  extracted(t1, (int) sv.getQuantiles()[i], n);
    //}

    //System.out.println(Arrays.toString(tb1));
    //System.out.println(tb1.length);

    float absErrorSumOrig[] = new float[] { 0 };
    float absErrorSumInterp[] = new float[] { 0 };
    int count = 0;

    float[] values = getGaussian(123L);
    KllFloatsSketch gaussianKll = createMockSketch(values, 500);
    float min = values[0], max = values[values.length - 1];
    for (int i = -5; i < 110; i += 1) {
      float val = min + (max - min) * i / 100;
      //for (float val : values) {
      //  int i = 0;
      double originalRank = gaussianKll.getSortedView().getRank(val, QuantileSearchCriteria.EXCLUSIVE);
      double interPolatedRank = getInterpolatedRank(gaussianKll, val);

      int valIdx = Arrays.binarySearch(values, val);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp = (double) valIdx / values.length;

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interPolatedRank - exp);
      absErrorSumOrig[0] += origError;
      absErrorSumInterp[0] += interpError;
      count++;

      System.out.println(
          "i: " + i + " val " + val + " valIdx " + valIdx + " orig " + fmt(originalRank) + " interp " + fmt(
              interPolatedRank) + " exp " + exp + " oe " + fmt(origError) + " ie " + fmt(interpError));
    }
    System.out.println("avg error orig: " + String.format("%.5f", absErrorSumOrig[0] / count));
    System.out.println("avg error interpolated: " + String.format("%.5f", absErrorSumInterp[0] / count));
  }

  public static @NotNull KllFloatsSketch createMockSketch(float[] values, int numQuantiles) {
    float[] quantiles = new float[numQuantiles];
    long[] cumWeights = new long[quantiles.length];
    float min = values[0], max = values[values.length - 1];
    quantiles[0] = min;
    quantiles[quantiles.length - 1] = max;
    for (int i = 1; i < quantiles.length - 1; i++) {
      quantiles[i] = min + (max - min) * ((float) i) / (quantiles.length - 1);
    }
    int idx = 0;
    System.out.println("max: " + max);
    System.out.println("quantiles " + Arrays.toString(quantiles));
    System.out.println("values " + Arrays.toString(values));
    for (int i = 0; i < values.length; i++) {
      //if (idx >= cumWeights.length) {
      //  System.out.println("val " + values[i] + " oob " + idx);
      //}
      while (values[i] > quantiles[idx]) {
        //System.out.println("val " + values[i] + " cmp " + cumWeights[idx] + " idx " + idx);
        cumWeights[idx] = i + 1;
        idx += 1;
      }
    }
    for (; idx < cumWeights.length; idx++) {
      cumWeights[idx] = cumWeights[idx - 1];
    }
    System.out.println(Arrays.toString(quantiles));
    System.out.println(Arrays.toString(cumWeights));
    return createMockSketch(quantiles, cumWeights);
  }

  private static float[] getGaussian(long seed) {
    Random rng = new Random(seed);
    float[] vals = new float[10000];
    for (int i = 0; i < vals.length; i++) {
      vals[i] = (float) rng.nextGaussian(1000, 100);
    }
    Arrays.sort(vals);
    return vals;
  }

  /**
   * Creates a mock sketch with a linear CDF.
   * It corresponds to the order statistics over the integers 1 until 100 (both inclusive).
   */
  private static @NotNull KllFloatsSketch getLinearKllFloatSketch1to100() {
    float[] quantiles = new float[] { 1f, 10f, 30f, 50f, 70f, 90f, 100f };
    long[] cumWeights = new long[] { 1L, 10L, 30L, 50L, 70L, 90L, 100L };
    return createMockSketch(quantiles, cumWeights);
  }

  public static @NotNull KllFloatsSketch createMockSketch(float[] quantiles, long[] cumWeights) {
    QuantilesFloatsAPI qfa = mock(QuantilesFloatsAPI.class);
    when(qfa.getMinItem()).thenReturn(quantiles[0]);
    when(qfa.getMaxItem()).thenReturn(quantiles[quantiles.length - 1]);
    when(qfa.getN()).thenReturn(cumWeights[cumWeights.length - 1]);

    FloatsSketchSortedView sv = new FloatsSketchSortedView(quantiles, cumWeights, qfa);
    KllFloatsSketch t1 = mock(KllFloatsSketch.class);
    when(t1.getSortedView()).thenReturn(sv);
    return t1;
  }

  private static void check(KllFloatsSketch t1, int i, long n) {
    String info = "i=" + String.format("%3s", i) + ": ";
    double r = getInterpolatedRank(t1, i);
    info += " r=" + String.format("%4s", r);
    double v = lessThanOrEqualSelectivity(t1, i);
    //double gt = greaterThanSelectivity(t1, i);
    double exp = (double) i / n;
    System.out.println(info);
  }
}

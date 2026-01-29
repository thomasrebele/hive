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
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.datasketches.kll.KllFloatsSketch;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Objects;

import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.betweenSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.greaterThanOrEqualSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.greaterThanSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.isHistogramAvailable;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.lessThanOrEqualSelectivity;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.lessThanSelectivity;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestFilterSelectivityEstimator {

  private static final SqlBinaryOperator GT = SqlStdOperatorTable.GREATER_THAN;
  private static final SqlBinaryOperator GE = SqlStdOperatorTable.GREATER_THAN_OR_EQUAL;
  private static final SqlBinaryOperator LT = SqlStdOperatorTable.LESS_THAN;
  private static final SqlBinaryOperator LE = SqlStdOperatorTable.LESS_THAN_OR_EQUAL;

  private static final float[] VALUES = { 1, 2, 2, 2, 2, 2, 2, 2, 3, 4, 5, 6, 7 };
  private static final float[] VALUES2 = {
      //
      10f, 100f, 1_000f, 10_000f, 100_000f, 1_000_000f, 10_000_000f,
      // TODO tr comments
      Math.nextDown(Math.nextDown(100f)), Math.nextDown(100f), Math.nextUp(1000f), Math.nextUp(Math.nextUp(1000f)), };
  private static final KllFloatsSketch KLL = StatisticsTestUtils.createKll(VALUES);
  private static final KllFloatsSketch KLL2 = StatisticsTestUtils.createKll(VALUES2);
  private static final float DELTA = 1e-7f;
  // a selectivity resolution of 1e-8f is enough to distinguish 10 million elements
  private static final RexBuilder REX_BUILDER = new RexBuilder(new JavaTypeFactoryImpl(new HiveTypeSystemImpl()));
  private static final RelDataTypeFactory TYPE_FACTORY = REX_BUILDER.getTypeFactory();

  public static final RelDataType BIGINT = REX_BUILDER.getTypeFactory().createSqlType(SqlTypeName.BIGINT);
  public static final RelDataType DECIMAL_3_1 = createDecimalType(3, 1);
  public static final RelDataType DECIMAL_7_1 = createDecimalType(7, 1);
  public static final RelDataType DECIMAL_38_25 = createDecimalType(38, 25);
  public static final RelDataType FLOAT = REX_BUILDER.getTypeFactory().createSqlType(SqlTypeName.FLOAT);
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
  private static ColStatistics stats2;

  @Mock
  private RelOptSchema schemaMock;
  @Mock
  private RelOptHiveTable tableMock;
  @Mock
  private RelMetadataQuery mq;

  private HiveTableScan tableScan;
  private RelNode scan;
  private final MutableObject<float[]> currentValues = new MutableObject<>();

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
    stats.setRange(rangeOf(VALUES));

    stats2 = new ColStatistics();
    stats2.setHistogram(KLL2.toByteArray());
    stats2.setRange(rangeOf(VALUES2));
  }

  private static ColStatistics.Range rangeOf(float[] values) {
    float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
    for (float v : values) {
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    return new ColStatistics.Range(min, max);
  }

  @Before
  public void before() {
    currentValues.setValue(VALUES);
    doReturn(tableType).when(tableMock).getRowType();
    when(tableMock.getRowCount()).thenAnswer(a -> (double) Objects.requireNonNull(currentValues.getValue()).length);

    RelBuilder relBuilder = HiveRelFactories.HIVE_BUILDER.create(relOptCluster, schemaMock);
    tableScan = new HiveTableScan(relOptCluster, relOptCluster.traitSetOf(HiveRelNode.CONVENTION),
        tableMock, "table", null, false, false);
    scan = relBuilder.push(tableScan).build();
    inputRef0 = REX_BUILDER.makeInputRef(scan, 0);
  }

  private void useValues(float[] values, ColStatistics stats) {
    currentValues.setValue(values);
    doReturn(Collections.singletonList(stats)).when(tableMock).getColStat(Collections.singletonList(0));
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

  @Test
  public void testComputeRangePredicateSelectivityWithCast() {
    useValues(VALUES, stats);
    checkSelectivity(3 / 13.f, compareWithCast(BIGINT, GE, int5));
  }

  @Test
  public void testComputeRangePredicateSelectivityWithCast2() {
    useValues(VALUES2, stats2);
    checkSelectivity(2 / 11.f, compareWithCast(DECIMAL_7_1, GE, literal(9000)));
  }

  @Test
  public void testComputeRangePredicateSelectivityBetweenWithCast() {
    useValues(VALUES2, stats2);
    RexNode cast = REX_BUILDER.makeCast(DECIMAL_38_25, inputRef0);
    //RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, cast, int1, int3);
    //RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, cast, literal(1), literal(3));
    RexNode filter = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolFalse, cast, literal(100), literal(1000));
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    System.out.println(filter);
    // expected: 10_000f, 100_000f, because CAST(1_000_000 AS DECIMAL(7,1)) = NULL, and similar for even larger values
    Assert.assertEquals(2 / 11.f, estimator.estimateSelectivity(filter), DELTA);

    RexNode filter2 = REX_BUILDER.makeCall(HiveBetween.INSTANCE, boolTrue, cast, literal(100), literal(1000));
    Assert.assertEquals(9 / 11.f, estimator.estimateSelectivity(filter2), DELTA);
  }

  private RexNode literal(float f) {
    return REX_BUILDER.makeLiteral(f, FLOAT);
  }

  private void checkSelectivity(float expected, RexNode filter) {
    FilterSelectivityEstimator estimator = new FilterSelectivityEstimator(scan, mq);
    Assert.assertEquals(filter.toString(), expected, estimator.estimateSelectivity(filter), DELTA);

    // swap operand
    RexCall call = (RexCall) filter;
    SqlOperator operator = ((RexCall) filter).getOperator();
    SqlOperator swappedOp;
    if (operator == LE) {
      swappedOp = GE;
    } else if (operator == LT) {
      swappedOp = GT;
    } else if (operator == GE) {
      swappedOp = LE;
    } else if (operator == GT) {
      swappedOp = LT;
    } else {
      throw new UnsupportedOperationException();
    }
    RexNode swapped = REX_BUILDER.makeCall(swappedOp, call.getOperands().get(1), call.getOperands().get(0));
    Assert.assertEquals(filter.toString(), expected, estimator.estimateSelectivity(swapped), DELTA);
  }

  private static RexNode compare(SqlBinaryOperator op, RexNode value) {
    return REX_BUILDER.makeCall(op, inputRef0, value);
  }

  private static RexNode compareWithCast(RelDataType type, SqlBinaryOperator op, RexNode value) {
    RexNode cast = REX_BUILDER.makeCast(type, inputRef0);
    return REX_BUILDER.makeCall(op, cast, value);
  }

  private static RelDataType createDecimalType(int precision, int scale) {
    return REX_BUILDER.getTypeFactory().createSqlType(SqlTypeName.DECIMAL, precision, scale);
  }

  @Test
  public void test() {
    System.out.println(FilterSelectivityEstimator.rangedSelectivity(KLL, 0, 2.1f) * VALUES.length);
    System.out.println(FilterSelectivityEstimator.rangedSelectivity(KLL, 2f, 2.1f) * VALUES.length);
  }
}

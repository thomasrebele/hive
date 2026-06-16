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

package org.apache.hadoop.hive.ql.parse;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConfForTest;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.ql.Context;
import org.apache.hadoop.hive.ql.QueryState;
import org.apache.hadoop.hive.ql.exec.Operator;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class TestCalcitePlanner {
  public static final String EXPLAIN_CONF_VAR = "hive.log.explain.output";
  static QueryState queryState;
  static HiveConf conf;

  ParseDriver pd;
  SemanticAnalyzer sA;
  private static Hive db;

  @BeforeClass
  public static void initialize() throws Exception {
    conf = new HiveConfForTest(TestCalcitePlanner.class);
    conf.set("hive.security.authorization.enabled", "false");
    conf.set("hive.security.authorization.manager",
        "org.apache.hadoop.hive.ql.security.authorization.plugin.sqlstd.SQLStdConfOnlyAuthorizerFactory");
    conf.set(EXPLAIN_CONF_VAR, "true");
    queryState =
        new QueryState.Builder().withHiveConf(conf).build();

    SessionState.start(conf);
  }


  @Before
  public void setup() throws SemanticException {
    pd = new ParseDriver();
    sA = new CalcitePlanner(queryState);
  }

  ASTNode parse(String query) throws ParseException {
    ASTNode nd = pd.parse(query).getTree();
    return (ASTNode) nd.getChild(0);
  }

  @Test
  public void testExtractSubQueries() throws Exception {
    HiveConf.ConfVars var = HiveConf.ConfVars.HIVE_LOG_EXPLAIN_OUTPUT;
    boolean boolVar = conf.getBoolVar(var);
    assertTrue(boolVar);

    ASTNode ast = parse("select 1 from table(values(1)) as t(a)");
    CalcitePlanner calcitePlanner = new CalcitePlanner(queryState);
    Context ctx = new Context(conf);
    calcitePlanner.init(false);
    calcitePlanner.initCtx(ctx);
    SemanticAnalyzer.PlannerContext pctx = new CalcitePlanner.PreCboCtx();
    calcitePlanner.genResolvedParseTree(ast, pctx);
    Operator operator = calcitePlanner.genOPTree(ast, pctx);

    String calcitePlan = ctx.getCalcitePlan();
    System.out.println(calcitePlan);
  }

}

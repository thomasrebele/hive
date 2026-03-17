package org.apache.hadoop.hive.ql.parse;

import org.apache.hadoop.hive.ql.plan.Explain;
import org.apache.hadoop.hive.ql.plan.OperatorDesc;
import org.apache.hive.common.util.AnnotationUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

public class TRUtil {

  public static String getOperatorTreeString(org.apache.hadoop.hive.ql.exec.Operator<?> op) {
    return getOperatorTreeString(op, 0);
  }

  public static String getOperatorTreeString(org.apache.hadoop.hive.ql.exec.Operator<?> op, int level) {
    if (op == null) return "";

    // Create an indentation based on the tree depth
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < level; i++) sb.append(i == level-1?"|-" : "| ");

    // op.getName() gets the Operator type (e.g., "TS", "FIL", "GBY")
    // op.getConf() gets the OperatorDesc which contains the columns, filters, etc.
    sb.append(op.getName());
    OperatorDesc conf = op.getConf();
    if (conf != null) {

      Explain annot = AnnotationUtils.getAnnotation(conf.getClass(), Explain.class);
      sb.append(" [");
      if (annot != null) {
        sb.append(toExplainStr(conf));
      }
      else {
        sb.append(conf.toString());
      }
      sb.append("]");
    }
    sb.append("\n");

    // Recursively append children
    if (op.getChildOperators() != null) {
      for (org.apache.hadoop.hive.ql.exec.Operator<?> child : op.getChildOperators()) {
        sb.append(getOperatorTreeString(child, level + 1));
      }
    }
    return sb.toString();
  }

  public static String toExplainStr(Object o) {
    StringBuilder r = new StringBuilder();
    Method[] methods = o.getClass().getMethods();
    for (Method m : methods) {
      Explain note = AnnotationUtils.getAnnotation(m, Explain.class);
      if (note == null) continue;

      boolean display = true;
      if(!display) continue;

      Object val = null;
      try {
        val = m.invoke(o);
      } catch (IllegalAccessException | InvocationTargetException ex) {
        throw new RuntimeException(ex);
      }

      if (val == null)
        continue;

      r.append(m.getName()).append(Arrays.toString(note.explainLevels())).append(": ").append(toStr(val));
      r.append("; ");
    }

    return r.toString();
  }

  private static String toStr(Object val) {
    if (val == null) {
      return "null";
    }

    if (val.getClass().isPrimitive()) {
      return Objects.toString(val);
    }

    if (val.getClass().isArray()) {
      return Arrays.toString((Object[])val);
    }

    return Objects.toString(val);
  }

}

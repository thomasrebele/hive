package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.quantilescommon.FloatsSketchSortedView;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.apache.datasketches.tdigest.TDigestDouble;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;

import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.getInterpolatedRank;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.TestFilterSelectivityEstimator.createMockSketch;

public class TestSketchInterpolation {


  public static String fmt(double val) {
    return String.format("%.4f", val);
  }

  public static String fmtAlt(double val) {
    return String.format("%.8f", val);
  }

  @Test
  public void test() {
    long seed = 2729069727529L; //System.nanoTime();
    System.out.println("seed: " + seed);
    Random rng = new Random(seed);
    float[] vals = new float[100000];
    for (int i = 0; i < vals.length; i++) {
      vals[i] = (float) rng.nextGaussian(1000, 100);
    }

    KllFloatsSketch sketch = KllFloatsSketch.newHeapInstance(8);
    TDigestDouble tdigest = new TDigestDouble((short)10);


    for(float value : vals) {
      sketch.update(value);
      tdigest.update(value);
    }

    Arrays.sort(vals);
    FloatsSketchSortedView sortedView = sketch.getSortedView();
    //sketch = TestFilterSelectivityEstimator.createMockSketch(vals, 200);

    System.out.println("kll size: " + sketch.toByteArray().length);
    System.out.println("tdigest size: " + tdigest.toByteArray().length);

    // for diagram
    //{
    //  long[] cumulativeWeights = sortedView.getCumulativeWeights();
    //  float[] quantiles = sortedView.getQuantiles();
    //  for (int i = 0; i < quantiles.length; i++) {
    //    System.out.println(
    //        "kll\t" + quantiles[i] + "\t" + (float) cumulativeWeights[i] / cumulativeWeights[cumulativeWeights.length - 1]);
    //  }

    {
      long[] cumulativeWeights = sortedView.getCumulativeWeights();
      float[] quantiles = sortedView.getQuantiles();
      for (int i = 0; i < quantiles.length; i++) {
        System.out.println(
            "kll-interp\t" + quantiles[i] + "\t" + (float) FilterSelectivityEstimator.getInterpolatedRank(sketch,
                quantiles[i]));
      }
    }

    //  for (int i = 0; i < quantiles.length; i++) {
    //    float val = quantiles[i];
    //    int valIdx = Arrays.binarySearch(vals, val);
    //    System.out.println("exp\t" + quantiles[i] + "\t" + ((float) valIdx) / (vals.length - 1));
    //  }

    //  for (int i = 0; i < quantiles.length; i++) {
    //    float val = quantiles[i];
    //    double tdigestRank = tdigest.getRank(val);
    //    System.out.println("tdigest\t" + quantiles[i] + "\t" + tdigestRank);
    //  }
    //}

    if(sketch.toByteArray() != null)
    System.out.println("kll size: " + sketch.toByteArray().length);
    System.out.println("tdigest size: " + tdigest.toByteArray().length);

    double origErrorSum = 0;
    double interpErrorSum = 0;
    double tdigestErrorSum = 0;

    System.out.println("\n" + "quantiles");
    float[] quantiles = sortedView.getQuantiles();
    for (float val : quantiles) {
      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank = getInterpolatedRank(sketch, val);
      double tdigestRank = tdigest.getRank(val);

      int valIdx = Arrays.binarySearch(vals, val);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp = (double) valIdx / (vals.length - 1);

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);
      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;
    }

    System.out.println("avg orig error: " + fmt(origErrorSum / quantiles.length));
    System.out.println("avg interpolated error: " + fmt(interpErrorSum / quantiles.length));
    System.out.println("avg tdigest error: " + fmt(tdigestErrorSum / quantiles.length));
    System.out.println("weights len: " + fmt(sortedView.getCumulativeWeights().length));

    //    System.out.println("\n" + "all values");
    //    origErrorSum = 0;
    //    interpErrorSum = 0;
    //    tdigestErrorSum = 0;
    //    for(float val : vals) {
    //      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
    //      double interpolatedRank = getInterpolatedRank(sketch, val);
    //      double tdigestRank = tdigest.getRank(val);
    //
    //      int valIdx = Arrays.binarySearch(vals, val);
    //      if (valIdx < 0)
    //        valIdx = -(valIdx + 1);
    //      double exp = (double) valIdx / (vals.length-1);
    //
    //      double origError = Math.abs(originalRank - exp);
    //      double interpError = Math.abs(interpolatedRank - exp);
    //      double tdigestError = Math.abs(tdigestRank - exp);
    //      origErrorSum += origError;
    //      interpErrorSum += interpError;
    //      tdigestErrorSum += tdigestError;
    //    }
    //
    //    System.out.println("avg orig error: " + fmt( origErrorSum/vals.length));
    //    System.out.println("avg interpolated error: " + fmt( interpErrorSum/vals.length));
    //    System.out.println("avg tdigest error: " + fmt( tdigestErrorSum/vals.length));
    //    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));
    //
    //    System.out.println("\n" + "steps");
    //    origErrorSum = 0;
    //    interpErrorSum = 0;
    //    tdigestErrorSum = 0;
    //    int steps = 0;
    //    float stepDiff = (vals[vals.length-1] - vals[0]) / (steps-1);
    //    //System.out.println("min " + vals[0] + " max " + vals[vals.length-1]);
    //    for(int i=0; i<steps; i++) {
    //      float val = vals[0] + i * stepDiff;
    //      //System.out.println(val);
    //
    //      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
    //      double interpolatedRank = getInterpolatedRank(sketch, val);
    //      double tdigestRank = tdigest.getRank(val);
    //
    //      int valIdx = Arrays.binarySearch(vals, val);
    //      if (valIdx < 0)
    //        valIdx = -(valIdx + 1);
    //      double exp = (double) valIdx / (vals.length-1);
    //
    //      double origError = Math.abs(originalRank - exp);
    //      double interpError = Math.abs(interpolatedRank - exp);
    //      double tdigestError = Math.abs(tdigestRank - exp);
    //      origErrorSum += origError;
    //      interpErrorSum += interpError;
    //      tdigestErrorSum += tdigestError;
    //
    //      //System.out.println("i " + i + " val " + fmt(val) + " exp " + fmt(exp)
    //      //    + " or " + fmt(originalRank) + " ir " + fmt(interpolatedRank)
    //      //    + " oe " + fmt(origError) + " ie " + fmt(interpError));
    //    }
    //
    //    System.out.println("avg orig error: " + fmt( origErrorSum/steps));
    //    System.out.println("avg interpolated error: " + fmt(interpErrorSum/steps));
    //    System.out.println("avg tdigest error: " + fmt(tdigestErrorSum/steps));
    //    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));

    System.out.println("\n" + "pairs");
    origErrorSum = 0;
    interpErrorSum = 0;
    tdigestErrorSum = 0;

    double origRelErrorProd = 1;
    double interpRelErrorProd = 1;
    double tdigestRelErrorProd = 1;

    int pairs = 20;

    DoubleUnaryOperator clamp = v -> Math.max(v, 1. / vals.length);
    DoubleBinaryOperator fn =
        (a, b) -> Math.max(clamp.applyAsDouble(a), clamp.applyAsDouble(b)) / Math.min(clamp.applyAsDouble(a),
            clamp.applyAsDouble(b));
    for(int i=0; i<pairs; i++) {
      float valA = (float) rng.nextGaussian(1000, 100);
      float valB = (float) rng.nextGaussian(1000, 100);

      float val1 = Math.min(valA, valB);
      float val2 = Math.max(valA, valB);

      double originalRank1 = sortedView.getRank(val1, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank1 = getInterpolatedRank(sketch, val1);
      double tdigestRank1 = tdigest.getRank(val1);

      int valIdx = Arrays.binarySearch(vals, val1);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp1 = (double) valIdx / (vals.length-1);

      double originalRank2 = sortedView.getRank(val2, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank2 = getInterpolatedRank(sketch, val2);
      double tdigestRank2 = tdigest.getRank(val2);

      int valIdx2 = Arrays.binarySearch(vals, val2);
      if (valIdx2 < 0)
        valIdx2 = -(valIdx2 + 1);
      double exp2 = (double) valIdx2 / (vals.length-1);

      double exp = exp2-exp1;
      double originalRank = originalRank2-originalRank1;
      if (originalRank == 0)
        originalRank = sketch.getNormalizedRankError(false);
      double interpolatedRank = interpolatedRank2-interpolatedRank1;
      //if (interpolatedRank == 0)
      //  interpolatedRank = 1.0 / vals.length;
      double tdigestRank = tdigestRank2-tdigestRank1;

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);

      System.out.println("ranks: exp " + fmtAlt(exp) + " orig " + fmtAlt(originalRank) + " interp " + fmtAlt(
          interpolatedRank) + " tdigest " + fmtAlt(tdigestRank));
      System.out.println(
          "  geom error " + fn.applyAsDouble(originalRank, exp) + " " + fn.applyAsDouble(interpolatedRank,
              exp) + " " + fn.applyAsDouble(tdigestRank, exp));

      origRelErrorProd *= fn.applyAsDouble(originalRank, exp);
      interpRelErrorProd *= fn.applyAsDouble(interpolatedRank, exp);
      tdigestRelErrorProd *= fn.applyAsDouble(tdigestRank, exp);

      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;
    }

    origRelErrorProd = Math.pow(origRelErrorProd, 1. / pairs);
    interpRelErrorProd = Math.pow(interpRelErrorProd, 1. / pairs);
    tdigestRelErrorProd = Math.pow(tdigestRelErrorProd, 1. / pairs);

    System.out.println("avg orig error: " + fmt(origErrorSum / pairs));
    System.out.println("avg interpolated error: " + fmt(interpErrorSum / pairs));
    System.out.println("avg tdigest error: " + fmt(tdigestErrorSum / pairs));

    System.out.println("geometric orig error: " + fmt(origRelErrorProd));
    System.out.println("geometric interpolated error: " + fmt(interpRelErrorProd));
    System.out.println("geometric tdigest error: " + fmt(tdigestRelErrorProd));

    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));
  }

}

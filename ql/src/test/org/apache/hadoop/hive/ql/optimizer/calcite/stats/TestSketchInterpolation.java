package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.quantilescommon.FloatsSketchSortedView;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.apache.datasketches.tdigest.TDigestDouble;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.FilterSelectivityEstimator.getInterpolatedRank;
import static org.apache.hadoop.hive.ql.optimizer.calcite.stats.TestFilterSelectivityEstimator.createMockSketch;

public class TestSketchInterpolation {


  public static String fmt(double val) {
    return String.format("%.4f", val);
  }

  private FloatsSketchSortedView deduplicate(FloatsSketchSortedView sortedView, float[] vals) {
    long[] orig = sortedView.getCumulativeWeights();
    float[] origQuantiles = sortedView.getQuantiles();
    int len = 0;

    long lastWeight = -1;
    for(long l : orig) {
      if(lastWeight != l) {
        len += 1;
        lastWeight = l;
      }
    }

    float[] quantiles = new float[len];
    long[] cumWeights = new long[len];

    lastWeight = -1;
    int idx = 0;
    for(int i=0; i<orig.length; i++) {
      if(lastWeight != orig[i]) {
        quantiles[idx] = origQuantiles[i];

        if(vals != null) {
          int valIdx = Arrays.binarySearch(vals, quantiles[idx]);
          if (valIdx < 0)
            valIdx = -(valIdx + 1);
          cumWeights[idx] = valIdx;
        }
        else {
          cumWeights[idx] = orig[i];
        }

        idx++;
        lastWeight = orig[i];
      }
    }
    return createMockSketch(quantiles, cumWeights).getSortedView();
  }

  @Test
  public void test() {
    Random rng = new Random(System.nanoTime());
    float[] vals = new float[1000000];
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
    FloatsSketchSortedView sortedView = deduplicate(sketch.getSortedView(), null /*vals*/);
    sketch = TestFilterSelectivityEstimator.createMockSketch(vals, 200);

    if(sketch.toByteArray() != null)
    System.out.println("kll size: " + sketch.toByteArray().length);
    System.out.println("tdigest size: " + tdigest.toByteArray().length);

    System.out.println("\n" + "quantiles");
    double origErrorSum = 0;
    double interpErrorSum = 0;
    double tdigestErrorSum = 0;
    float[] quantiles = sortedView.getQuantiles();
    for(float val : quantiles) {
      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank = getInterpolatedRank(sketch, val);
      double tdigestRank = tdigest.getRank(val);

      int valIdx = Arrays.binarySearch(vals, val);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp = (double) valIdx / (vals.length-1);

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);
      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;
    }

    System.out.println("avg orig error: " + fmt( origErrorSum/vals.length));
    System.out.println("avg interpolated error: " + fmt( interpErrorSum/vals.length));
    System.out.println("avg tdigest error: " + fmt( tdigestErrorSum/vals.length));
    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));

    System.out.println("\n" + "all values");
    origErrorSum = 0;
    interpErrorSum = 0;
    tdigestErrorSum = 0;
    for(float val : vals) {
      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank = getInterpolatedRank(sketch, val);
      double tdigestRank = tdigest.getRank(val);

      int valIdx = Arrays.binarySearch(vals, val);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp = (double) valIdx / (vals.length-1);

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);
      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;
    }

    System.out.println("avg orig error: " + fmt( origErrorSum/vals.length));
    System.out.println("avg interpolated error: " + fmt( interpErrorSum/vals.length));
    System.out.println("avg tdigest error: " + fmt( tdigestErrorSum/vals.length));
    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));

    System.out.println("\n" + "steps");
    origErrorSum = 0;
    interpErrorSum = 0;
    tdigestErrorSum = 0;
    int steps = 10000;
    float stepDiff = (vals[vals.length-1] - vals[0]) / (steps-1);
    //System.out.println("min " + vals[0] + " max " + vals[vals.length-1]);
    for(int i=0; i<steps; i++) {
      float val = vals[0] + i * stepDiff;
      //System.out.println(val);

      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);
      double interpolatedRank = getInterpolatedRank(sketch, val);
      double tdigestRank = tdigest.getRank(val);

      int valIdx = Arrays.binarySearch(vals, val);
      if (valIdx < 0)
        valIdx = -(valIdx + 1);
      double exp = (double) valIdx / (vals.length-1);

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);
      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;

      //System.out.println("i " + i + " val " + fmt(val) + " exp " + fmt(exp)
      //    + " or " + fmt(originalRank) + " ir " + fmt(interpolatedRank)
      //    + " oe " + fmt(origError) + " ie " + fmt(interpError));
    }

    System.out.println("avg orig error: " + fmt( origErrorSum/steps));
    System.out.println("avg interpolated error: " + fmt(interpErrorSum/steps));
    System.out.println("avg tdigest error: " + fmt(tdigestErrorSum/vals.length));
    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));

    System.out.println("\n" + "pairs");
    origErrorSum = 0;
    interpErrorSum = 0;
    tdigestErrorSum = 0;
    int pairs = 1000;
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
      double interpolatedRank = interpolatedRank2-interpolatedRank1;
      double tdigestRank = tdigestRank2-tdigestRank1;

      double origError = Math.abs(originalRank - exp);
      double interpError = Math.abs(interpolatedRank - exp);
      double tdigestError = Math.abs(tdigestRank - exp);
      origErrorSum += origError;
      interpErrorSum += interpError;
      tdigestErrorSum += tdigestError;
    }

    System.out.println("avg orig error: " + fmt( origErrorSum/steps));
    System.out.println("avg interpolated error: " + fmt( interpErrorSum/steps));
    System.out.println("avg tdigest error: " + fmt( tdigestErrorSum/vals.length));
    System.out.println("weights len: " + fmt( sortedView.getCumulativeWeights().length));
  }

}

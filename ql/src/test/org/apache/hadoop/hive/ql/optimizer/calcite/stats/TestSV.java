package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.quantilescommon.FloatsSketchSortedView;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class TestSV {

  public static String fmt(double val) {
    return String.format("%.4f", val);
  }

  @Test
  public void test() {
    long seed = 2729069727529L;
    System.out.println("seed: " + seed);
    Random rng = new Random(seed);
    float[] vals = new float[1];
    for (int i = 0; i < vals.length; i++) {
      vals[i] = (float) rng.nextGaussian(1000, 100);
    }

    KllFloatsSketch sketch = KllFloatsSketch.newHeapInstance(8);


    for(float value : vals) {
      sketch.update(value);
    }


    Arrays.sort(vals);
    FloatsSketchSortedView sortedView;
    sortedView = sketch.getSortedView();

    if(sketch.toByteArray() != null)
      System.out.println("kll size: " + sketch.toByteArray().length);

    double origErrorSum = 0;

    System.out.println("\n" + "quantiles");
    float[] quantiles = sortedView.getQuantiles();
    long[] cumulativeWeights = sortedView.getCumulativeWeights();

    for (int i=0; i<quantiles.length; i++) {
      float val = quantiles[i];

      double originalRank = sortedView.getRank(val, QuantileSearchCriteria.EXCLUSIVE);

      int valIdx = Arrays.binarySearch(vals, val);
      int correctedValIdx = valIdx;
      if (correctedValIdx < 0)
        correctedValIdx = -(correctedValIdx + 1);
      double exp = (double) correctedValIdx / (vals.length - 1);

      double origError = Math.abs(originalRank - exp);
      origErrorSum += origError;

      System.out.println("i " + String.format("%3d", i) //
          + " val " + String.format("%9.4f", val) //
          + " valIdx " + String.format("%3d", valIdx) //
          + " cw " + String.format("%3d", cumulativeWeights[i])
          + " original rank " + fmt(originalRank) //
          + " exp " + fmt(exp));
    }

    System.out.println("avg orig error: " + fmt(origErrorSum / quantiles.length));
    System.out.println("weights len: " + fmt(sortedView.getCumulativeWeights().length));

  }
}

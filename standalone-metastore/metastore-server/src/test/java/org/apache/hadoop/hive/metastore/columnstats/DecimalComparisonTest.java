package org.apache.hadoop.hive.metastore.columnstats;

import com.google.common.math.BigIntegerMath;
import org.apache.hadoop.hive.metastore.api.Decimal;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.Approach;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.PAD_POS;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.PAD_NEG;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.bitLog;

public class DecimalComparisonTest {

  public static class ExpectApproach implements DecimalComparator.ApproachInfoCallback {
    public final Approach expected;

    public ExpectApproach(Approach expected) {
      this.expected = expected;
    }

    @Override
    public void accept(Approach approach) {
      assertEquals(expected, approach);
    }
  }

  public static final HexFormat FORMAT = HexFormat.ofDelimiter(" ");

  public String toStr(Decimal val) {
    // Hive's scale are the digits behind the dot ...
    return Objects.toString(new BigDecimal(new BigInteger(val.getUnscaled()), -val.getScale()));
  }


  public static Decimal createDecimal(BigDecimal bigDecimal, int scaleDrift) {
    byte[] byteArray = bigDecimal.multiply(BigDecimal.TEN.pow(scaleDrift)).unscaledValue().toByteArray();
    ByteBuffer wrap = ByteBuffer.wrap(byteArray);
    return new Decimal((short)(bigDecimal.scale()+scaleDrift), wrap);
  }


  public void check(String n1, String n2) {
    check(n1, 0, n2, 0);
  }

    public void check(String n1, int scaleDrift1, String n2, int scaleDrift2) {
      BigDecimal bd1 = new BigDecimal(n1), bd2 = new BigDecimal(n2);
      checkInner(bd1, scaleDrift1, bd2, scaleDrift2);
      checkInner(bd2, scaleDrift2, bd1, scaleDrift1);
    }

    public int normalizeCompareTo(int cmp) {
      return Integer.compare(cmp, 0);
    }

  public void checkInner(String n1, String n2) {
    BigDecimal bd1 = new BigDecimal(n1), bd2 = new BigDecimal(n2);
     checkInner(bd1, 0, bd1, 0);
  }

      public void checkInner(BigDecimal bd1, int scaleDrift1, BigDecimal bd2, int scaleDrift2) {
      Decimal d1 = createDecimal(bd1, scaleDrift1);
      Decimal d2 = createDecimal(bd2, scaleDrift2);

      int expected = normalizeCompareTo(bd1.compareTo(bd2));
      int actual = normalizeCompareTo(new DecimalComparator().compare(d1, d2));
      if(expected != actual) {
        System.out.println("compareTo result was wrong for " + toStr(d1) + " and " + toStr(d2) + ": " + expected + ", but was " + actual);
        assertEquals("compareTo result was wrong for " + bd1 + " and " + bd2, expected, actual);
      }
    }

  @Test
  public void test1() {

    checkInner("-1", "-10");
    checkInner("-10.2", "-123.2");
    checkInner("-123.2", "-10.21232");

    checkInner("-10", "-1");
    checkInner("-123.2", "-10.2");
    checkInner("-10.21232", "-123.2");

    // positive values
    check("50", "20");
    check("9.123", "8113");
    check("9123", "8.113");
    check("9123", "1.113");
    check("1123", "9.113");

    // mixed values
    check("1", "-1");
    check("123", "-123");
    check("123", "-11.1");
    check("123.23", "-11.1");
    check("123.23", "-11");

    // negative values
    check("-10", "-1");
    check("-2", "-8");
    check("-2E+1", "-8E+1");
    check("-50", "-20");
    check("-10.2", "-123.2");
    check("-10.21232", "-123.2");
  }

  @Test
  public void test2() {
    checkInner("10", "1");
    checkInner("1", "10");
    checkInner("-10", "-1");
    checkInner("-1", "-10");
  }

  @Test
  public void testSameScale() {
    check("2", "8");
    check("-2", "-8");
    check("20", "80");
    check("-20", "-80");
    check("1000", "1001");
    check("-1000", "-1001");
    check("100000000", "100000001");
    check("-100000000", "-100000001");
  }

  @Test public void testBitLog1() {
    interface Helper { void accept(int expected, int input);}
    Helper check = (expected, input) -> assertEquals(expected,
        bitLog(BigInteger.valueOf(input).toByteArray(), input >= 0 ? PAD_POS : PAD_NEG));

    check.accept(1, 1);
    check.accept(2, 2);
    check.accept(2, 3);
    check.accept(8, 255);
    check.accept(9, 256);
    check.accept(9, 511);
    check.accept(10, 512);
    check.accept(10, 1023);
    check.accept(11, 1024);

    check.accept(1, -1);
    check.accept(2, -2);
    check.accept(2, -3);
    check.accept(8, -255);
    check.accept(9, -256);
    check.accept(9, -511);
    check.accept(10, -512);
    check.accept(10, -1023);
    check.accept(11, -1024);

    // log2(5800) is about 12.5, bitLog floor(log(...))+1
    check.accept(13, 5800);
    check.accept(13, -5800);
  }

  @Test
  public void testBitLog2() {
    interface Helper { void accept(int expected, String input, byte pad);}
    Helper check = (expected, input, pad) ->
        assertEquals(expected, bitLog(FORMAT.parseHex(input), pad));

    check.accept(8, "FF 16", PAD_NEG);

    check.accept(0, "00 00 00", PAD_POS);
    check.accept(1, "00 00 01", PAD_POS);
    check.accept(2, "00 00 02", PAD_POS);
    check.accept(3, "00 00 04", PAD_POS);
    check.accept(4, "00 00 08", PAD_POS);
    check.accept(5, "00 00 10", PAD_POS);
    check.accept(5, "00 00 11", PAD_POS);
    check.accept(9, "00 01 11", PAD_POS);
    check.accept(17, "01 11 11", PAD_POS);
    check.accept(17, "01 00 00", PAD_POS);
    check.accept(17, "00 00 00 01 00 00", PAD_POS);

    check.accept(1, "FF FF FF", PAD_NEG);
    check.accept(2, "FF FF FE", PAD_NEG);
    check.accept(2, "FF FF FD", PAD_NEG);
    check.accept(3, "FF FF FB", PAD_NEG);
    check.accept(4, "FF FF F7", PAD_NEG);
    check.accept(5, "FF FF EF", PAD_NEG);
    check.accept(5, "FF FF EE", PAD_NEG);
    check.accept(9, "FF FE EE", PAD_NEG);
    check.accept(17, "FE EE EE", PAD_NEG);
    check.accept(17, "FE FF FF", PAD_NEG);
    check.accept(17, "FF FF FF FE FF FF", PAD_NEG);
  }

  @Test
  public void bitLenPostcondition() {
    // define helper which executes the asserts
    Consumer<BigInteger> check = x -> {
      int bl = bitLog(x.toByteArray(), x.signum() == -1 ? PAD_NEG : PAD_POS);
      int log = BigIntegerMath.log2(x.abs(), RoundingMode.DOWN);
      if(log<0) fail("Log may not be negative: " + x);

      if(log+1 != bl) fail(x.toString());
      // check inequalities
      if(!(bl-1 <= log)) fail(x.toString());
      if(!(log < bl)) fail(x.toString());
    };

    // check all integers from -18 to 18 (inclusive)
    for(int i=-18; i<=18; i++) {
      if(i==0) continue;
      BigInteger x = BigInteger.valueOf(i);
      check.accept(x);
    }

    // check powers of two from 2^5 to 2^200 (about 60 decimal digits)
    for(int i=5; i<200; i++) {
      BigInteger p = BigInteger.TWO.pow(i);
      // check negative and positive powers of two: -2^p and +2^p
      for(int neg=0; neg<2; neg++) {
        if(neg == 1) p = p.negate();

        // check the neighborhood of each power of two
        for(int j=-2; j<=2; j++) {
          BigInteger x = p.add(BigInteger.valueOf(j));
          if(x.compareTo(BigInteger.ZERO) == 0) continue;
          check.accept(x);
        }
      }
    }
  }

  @Test
  public void testRandomized1() {
    Random rOuter = new Random(System.nanoTime());

    int shift = 15;
    int minScale = -(1<<shift);
    int maxScale = (1<<shift)-1;

    int[] count = new int[Approach.END.ordinal()+1];
    int[] countError = new int[Approach.END.ordinal()+1];

    List<Throwable> errors = new ArrayList<>();
      for (int i = 0; i < 10000; i++) {
        long seed = rOuter.nextLong();
        int[] approachIdx = new int[]{1};
        try {
          randomInner(seed, minScale, maxScale, m -> {
            count[m.ordinal()] += 1;
            approachIdx[0] = m.ordinal();
          });
        }
        catch(Throwable t) {
          t.addSuppressed(new RuntimeException("seed was " + seed));
          countError[approachIdx[0]] += 1;
          errors.add(t);
        }
      }

    for (Approach m : Approach.values()) {
      System.out.println(m + ": " + count[m.ordinal()] + " errors: " + countError[m.ordinal()]);
    }
    if(!errors.isEmpty()) {
      errors.forEach(t -> System.out.println(t.getMessage()));
      AssertionError e = new AssertionError();
      errors.forEach(t -> e.addSuppressed(t));
      throw e;
    }
  }

  @Test
  public void testRandomized1tmp() {

    int shift = 4;
    int minScale = -(1<<shift);
    int maxScale = (1<<shift)-1;

    //randomInner(6476192887685342014l , minScale, maxScale, count);
    //randomInner(-952131642459718632l , minScale, maxScale, count);
    randomInner(-1088050332798435706l , minScale, maxScale, null);
  }


  private void randomInner(long seed, int minScale, int maxScale, DecimalComparator.ApproachInfoCallback aic) {
    Random r = new Random(seed);
    int len = r.nextInt(30) + 1;
    byte[] num = new byte[len];
    r.nextBytes(num);


    if (num[0] == 0)
      num[0] = (byte) (2 * r.nextInt(2) - 1);

    int scaleDrift1 = 3;
    int scaleDrift2 = 4;

    int s1 = r.nextInt(maxScale - scaleDrift1 - minScale) + minScale;
    int s2 = Math.clamp(s1 + (int) r.nextGaussian(0, 10), minScale, maxScale - scaleDrift2);

    byte[] num2 = Arrays.copyOf(num, num.length);
    num2[0] = (byte) r.nextInt();
    // ensure the numbers have the same sign
    num2[0] = (byte) ((num2[0] & 0x7f) | (num[0] & 0x80));

    int adapt = 0;
    for(int i=0; i<2 /*100*/*len; i++) {
      BigDecimal bd1 = new BigDecimal(new BigInteger(num), s1);
      BigDecimal bd2 = new BigDecimal(new BigInteger(num2), s2);


      int expected = normalizeCompareTo(bd1.compareTo(bd2));

      Decimal d1 = createDecimal(bd1, scaleDrift1);
      if (d1 == null) {
        fail("Could not convert " + bd1 + " to Decimal, seed " + seed);
      }
      Decimal d2 = createDecimal(bd2, scaleDrift2);
      if (d2 == null) {
        fail("Could not convert " + bd2 + " to Decimal, seed " + seed);
      }

      int[] approachIdx = new int[]{ Approach.UNKNOWN.ordinal()};
      int actual = new DecimalComparator().compareInner(d1, d2, false, m -> approachIdx[0] = m.ordinal());
      if(aic != null) aic.accept(Approach.values()[approachIdx[0]]);
      if (approachIdx[0] != Approach.FALLBACK.ordinal()) {
        if (expected != normalizeCompareTo(actual)) {
          String expOp = expected < 0 ? " < " : expected > 0 ? " > " : " = ";
          System.out.println(
              "compareTo result was wrong for\n  " + bd1 + "/" + toStr(d1) + " and\n  " + bd2 + "/" + toStr(
                  d2) + ": expected " + expected + ", but was " + actual + " with approach " + Approach.values()[approachIdx[0]]);
          assertEquals(
              "compareTo result was wrong for " + bd1 + expOp + bd2 + ", approach " + Approach.values()[approachIdx[0]] + ", seed " + seed,
              expected, actual);
        }
      }

      if(adapt == 0) {
        if ( bd1.signum() == 1) {
            adapt = expected > 0 ? 1 : 2;
        }
        else {
          adapt = expected > 0 ? 2 : 1;
        }
      }
      if (adapt == 1) {
        shiftRight(num);
      }
      else {
        shiftRight(num2);
      }


      if(bd1.unscaledValue().bitLength() == 0 || bd2.unscaledValue().bitLength() == 0) {
        break;
      }

    }
  }

  private void shiftRight(byte[] num) {
    int carry = num[0] & 0x80;
    for(int i=0; i<num.length; i++) {
      int nextCarry = (num[i]&0x1) << 7;
      // use &0xff to do an unsigned (!) right-shift
      int rightShifted = (num[i] & 0xff) >> 1;
      num[i] = (byte)((carry | rightShifted)&0xff);
      carry = nextCarry;
    }
  }


}

package org.apache.hadoop.hive.metastore.columnstats;

import com.google.common.math.BigIntegerMath;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.metastore.api.Decimal;
import org.apache.hadoop.hive.metastore.api.utils.DecimalUtils;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.Method;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.PAD_POS;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.PAD_NEG;
import static org.apache.hadoop.hive.metastore.columnstats.DecimalComparator.bitLog;

public class DecimalComparisonTest {



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
      //System.out.println();
      checkInner(n1, scaleDrift1, n2, scaleDrift2);
      checkInner(n2, scaleDrift2, n1, scaleDrift1);
    }

    public int normalizeCompareTo(int cmp) {
      return Integer.compare(cmp, 0);
    }

  public void checkInner(String n1, String n2) {
     checkInner(n1, 0, n2, 0);
  }

      public void checkInner(String n1, int scaleDrift1, String n2, int scaleDrift2) {
      BigDecimal bd1 = new BigDecimal(n1), bd2 = new BigDecimal(n2);

      Decimal d1 = createDecimal(bd1, scaleDrift1);
      Decimal d2 = createDecimal(bd2, scaleDrift2);

      int expected = normalizeCompareTo(bd1.compareTo(bd2));
      int actual = normalizeCompareTo(new DecimalComparator().compare(d1, d2));
      if(expected != actual) {
        System.out.println("compareTo result was wrong for " + n1 + "/" + toStr(d1) + " and " + n2 + "/" + toStr(d2) + ": " + expected + ", but was " + actual);
        assertEquals("compareTo result was wrong for " + n1 + " and " + n2, expected, actual);
      }
    }

  @Test
  public void testTmp() {
    //checkInner("8.113", "9123");

    String s1 = "1249894", s2 = "122492";
    BigInteger bi1 = new BigInteger(s1), bi2 = new BigInteger(s2);
    byte[] b1 = bi1.toByteArray();
    byte[] b2 = bi2.toByteArray();

    System.out.println(FORMAT.formatHex(b1));
    System.out.println(FORMAT.formatHex(b2));

    System.out.println(bi1.multiply(bi2));
    System.out.println(new BigInteger(b1).multiply(new BigInteger(b2)));



  }

  @Test
  public void test1() {

    checkInner("-1", "-10");
    System.out.println("\n\n");
    checkInner("-10.2", "-123.2");
    System.out.println("\n\n");
    checkInner("-123.2", "-10.21232");

    checkInner("-10", "-1");
    System.out.println("\n\n");
    checkInner("-123.2", "-10.2");
    System.out.println("\n\n");
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
    //checkInner("10", "1");
    //checkInner("1", "10");
    //checkInner("-10", "-1");
    //checkInner("-1", "-10");
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

  @Test
  public void scaleConfusion() {
    Decimal decimal = DecimalUtils.getDecimal(10, -1);
    HiveDecimal hiveDecimal = HiveDecimal.create(new BigInteger(decimal.getUnscaled()), decimal.getScale());
    System.out.println(hiveDecimal);

    System.out.println(toStr(decimal));

    Decimal decimal1 = createDecimal(new BigDecimal("10.123"), 100);
    System.out.println(toStr(decimal1));
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

    int[] count = new int[Method.END.ordinal()+1];
    int[] countError = new int[Method.END.ordinal()+1];

    List<Throwable> errors = new ArrayList<>();
      for (int i = 0; i < 1000000; i++) {
        long seed = rOuter.nextLong();
        int[] methodIdx = new int[]{1};
        try {
          randomInner(seed, minScale, maxScale, m -> {
            count[m.ordinal()] += 1;
            methodIdx[0] = m.ordinal();
          });
        }
        catch(Throwable t) {
          t.addSuppressed(new RuntimeException("seed was " + seed));
          countError[methodIdx[0]] += 1;
          errors.add(t);
        }
      }

    for (Method m : Method.values()) {
      System.out.println(m + ": " + count[m.ordinal()] + " errors: " + countError[m.ordinal()]);
    }
    if(!errors.isEmpty()) {
      errors.forEach(t -> System.out.println(t.getMessage())
      );
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


  private void randomInner(long seed, int minScale, int maxScale, DecimalComparator.MethodInfoCallback mic) {
    //System.out.println("seed: " + seed);
    Random r = new Random(seed);
    int len = r.nextInt(30) + 1;
    byte[] num = new byte[len];
    r.nextBytes(num);


    if (num[0] == 0)
      num[0] = (byte) (2 * r.nextInt(2) - 1);

    int scaleDrift1 = 3;
    int scaleDrift2 = 4;

    int s1 = -2; //r.nextInt(maxScale - scaleDrift1 - minScale) + minScale;
    int s2 = -1; //Math.clamp(s1 + (int) r.nextGaussian(0, 10), minScale, maxScale - scaleDrift2);

    byte[] num2 = Arrays.copyOf(num, num.length);
    num2[0] = (byte) r.nextInt();
    // ensure the numbers have the same sign
    num2[0] = (byte) ((num2[0] & 0x7f) | (num[0] & 0x80));

    num = BigInteger.valueOf(1523).toByteArray();
    num2 = BigInteger.TEN.pow(3).toByteArray();

    //System.out.println(FORMAT.formatHex(num) + "     " + FORMAT.formatHex(num2));

    int adapt = 0;
    for(int i=0; i<2 /*100*/*len; i++) {
      BigDecimal bd1 = new BigDecimal(new BigInteger(num), s1);
      BigDecimal bd2 = new BigDecimal(new BigInteger(num2), s2);


      int expected = normalizeCompareTo(bd1.compareTo(bd2));
      ;
      //System.out.println(bd1 + " vs " + bd2 + "  "
      //    + (expected > 0 ? bd1.floatValue() / bd2.floatValue() : bd2.floatValue() / bd1.floatValue() )
      //+ " log1 " + Math.log(bd1.floatValue())/Math.log(2) + " log2 " + Math.log(bd2.floatValue())/Math.log(2));

      Decimal d1 = createDecimal(bd1, scaleDrift1);
      if (d1 == null) {
        fail("Could not convert " + bd1 + " to Decimal, seed " + seed);
      }
      Decimal d2 = createDecimal(bd2, scaleDrift2);
      if (d2 == null) {
        fail("Could not convert " + bd2 + " to Decimal, seed " + seed);
      }

      int[] methodIdx = new int[]{Method.UNKNOWN.ordinal()};
      int actual = new DecimalComparator().compareToInner(d1, d2, false, m -> methodIdx[0] = m.ordinal());
      if(mic != null) mic.accept(Method.values()[methodIdx[0]]);
      if (methodIdx[0] != Method.FALLBACK.ordinal()) {
        if (expected != normalizeCompareTo(actual)) {
          String expOp = expected < 0 ? " < " : expected > 0 ? " > " : " = ";
          System.out.println(
              "compareTo result was wrong for\n  " + bd1 + "/" + toStr(d1) + " and\n  " + bd2 + "/" + toStr(
                  d2) + ": expected " + expected + ", but was " + actual + " with method " + Method.values()[methodIdx[0]]);
          assertEquals(
              "compareTo result was wrong for " + bd1 + expOp + bd2 + ", method " + Method.values()[methodIdx[0]] + ", seed " + seed,
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
        //System.out.println("break");
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


  @Test
  public void testTmp2() {
    byte[] bytes = FORMAT.parseHex("5b 7c");

    for(int i=0; i<32; i++) {
      shiftRight(bytes);
      System.out.println(FORMAT.formatHex(bytes));
      for(int j=0; j<bytes.length; j++) {
        System.out.print(Integer.toBinaryString(bytes[j]&0xff));
      }
      System.out.println();
    }
  }


  @Test
  public void test3() {

    int scale = 10;
    BigInteger b = BigInteger.valueOf(1000);
    BigDecimal bigDecimal = new BigDecimal(b, -scale);

    int bitLog = bitLog(b.toByteArray(), PAD_POS);
    int normScale = (scale * 27213) >> 13;
    int tmp = bitLog + normScale;

    double log = Math.log(bigDecimal.floatValue())/Math.log(2);
    System.out.println(tmp-1
        + "  bl-1 " + (bitLog-1)
        + "  ns " + normScale
        + " log " + log
        + " log(10^scale) " + Math.log(BigInteger.TEN.pow(scale).floatValue())/Math.log(2)
        + " log(b) " + Math.log(b.floatValue())/Math.log(2)
        + " num " + bigDecimal);
  }
}

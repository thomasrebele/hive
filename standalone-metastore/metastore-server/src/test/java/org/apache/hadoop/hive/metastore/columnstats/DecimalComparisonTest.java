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
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DecimalComparisonTest {

  static final byte PAD_POS = (byte) 0;
  static final byte PAD_NEG = (byte) 255;

  enum Method {
    SAME,
    UNKNOWN,
    SIGN,
    EQSCALE,
    LOG_ZERO,
    BITLEN_A,
    BITLEN_B,
    FALLBACK,

    END,
  }

  interface MethodInfoCallback extends Consumer<Method> {}

  public static final HexFormat FORMAT = HexFormat.ofDelimiter(" ");

  public String toStr(Decimal val) {
    // Hive's scale are the digits behind the dot ...
    return Objects.toString(new BigDecimal(new BigInteger(val.getUnscaled()), -val.getScale()));
  }

    /**
     * Note: 0 is interpreted as positive
     */
    private boolean positive(byte[] unscaled) {
      if(unscaled.length == 0) return true;
      return 0 == (Byte.toUnsignedInt(unscaled[0]) >>7);
    }

    public int compareTo(Decimal d1, Decimal d2, MethodInfoCallback mic) {
      return compareToInner(d1, d2, true, mic);
    }

  private int compareToInner(Decimal d1, Decimal d2, boolean useFallback, MethodInfoCallback mic) {
    byte[] b1 = d1.getUnscaled();
    byte[] b2 = d2.getUnscaled();
    boolean sign1 = positive(b1);
    boolean sign2 = positive(b2);

    if (sign1 != sign2) {
      if(mic != null) mic.accept(Method.SIGN);
      return (sign1 ? 1 : -1);
    }

    byte pad = sign1 ? PAD_POS : PAD_NEG;
    if (d1.getScale() == d2.getScale()) {
      return compareSameScale(pad, b1, b2, mic);
    }

    // Hive's scale are the digits behind the dot ...
    if (d1.getScale() < d2.getScale()) {
      return compareToScaleDiff(pad, b1, d1.getScale(), b2, d2.getScale(), useFallback, mic);
    }
    else {
      return -compareToScaleDiff(pad, b2, d2.getScale(), b1, d1.getScale(), useFallback, mic);
    }
  }

  private static int compareSameScale(byte pad, byte[] b1, byte[] b2, MethodInfoCallback mic) {
    int len = Math.max(b1.length, b2.length);
    int i1 = b1.length-len;
    int i2 = b2.length-len;
    for(int i=0; i<len; i++) {
      // TODO: test case
      byte c1 = i1 < 0 ? pad : b1[i1];
      byte c2 = i2 < 0 ? pad : b2[i2];
      if(c1 != c2) {
        int u1 = Byte.toUnsignedInt(c1);
        int u2 = Byte.toUnsignedInt(c2);
        if(mic != null) mic.accept(Method.EQSCALE);
        return u1 < u2 ? -1 : 1;
      }
      i1++;
      i2++;
    }
    return 0;
  }

  public static Decimal createDecimal(BigDecimal bigDecimal, int scaleDrift) {
    byte[] byteArray = bigDecimal.multiply(BigDecimal.TEN.pow(scaleDrift)).unscaledValue().toByteArray();
    ByteBuffer wrap = ByteBuffer.wrap(byteArray);
    return new Decimal((short)(bigDecimal.scale()+scaleDrift), wrap);
  }

  int findStart(byte[] b, byte pad) {
      for(int i=0; i<b.length; i++) {
        if (b[i] != pad) return i;
      }
      return b.length;
  }

  boolean isNegPowTwo(byte[] b, int start) {
    for(int i=start+1; i<b.length; i++) {
      if (b[i] != 0) return false;
    }

    // power of two if trailingZeros + leadingOnes == 8
    // calculate trailing zeros relative to a byte; OR with 0x100 to ensure its <=8
    byte first = b[start];
    int trailingZeros = Integer.numberOfTrailingZeros(first | 0x100);
    // calculate "complement relative to 8 bits = 1 byte" of leadingOnes
    // example: 0b11000000, invert bits 0b00111111, leading ones is 2, its complement is 8-2=6
    int leadingOnesComplement = 32 - Integer.numberOfLeadingZeros((Byte.toUnsignedInt(first))^0xff);
    return trailingZeros == leadingOnesComplement;
  }

  /**
   * Calculate the logarithm of the integer value represented by the two's complement stored in the byte array.
   * It holds that bitLog(abs(num))-1 <= log(abs(num)) < bitLog(abs(num)).
   * @return 0 if num is zero, else floor(log(abs(num))) + 1
   */
  int bitLog(byte[] num, byte pad) {
      int start = findStart(num, pad);
      if(start == num.length) return pad == PAD_POS ? 0 : 1;
      int first = num[start];
      int inv = (first^pad)&0xff;
      int bits = 32-Integer.numberOfLeadingZeros(inv);
    int result = (num.length - start - 1) * 8 + bits;
    // adjust bit log for value = -2^n
    boolean isNegPowTwo = pad == PAD_NEG && isNegPowTwo(num, start);
    return result + (isNegPowTwo ? 1 : 0);
  }

  /** Precondition: scale1 < scale2 */
  private int compareToScaleDiff(byte pad, byte[] b1, short scale1, byte[] b2, short scale2, boolean useFallback,
      MethodInfoCallback mic) {
    // if b1 and b2 are negative, we consider both their absolute value; the result needs to be negated
    // idea: estimate the number of bits if we multiplied b1 by 10^x to make the two arrays comparable
    // inequality in the continuous domain:
    // if decimal1 > decimal2 (eq1), or as both are positive, log2(decimal1) > log2(decimal2), then:
    // log2(decimal1) > log2(decimal2)
    // log2(b1*10^-scale1) > log2(b2*10^-scale2)
    // log2(b1) + log2(10)*(-scale1) > log2(b2) + log2(10)*(-scale2)
    // log2(b1)-log2(b2) + log2(10)*(scale2-scale1) > 0 (eq2)

    // discrete domain:
    // we want to get from (eq2) a condition (eq3) so that (eq3) => (eq1) holds,
    // or in other words: if eq3 holds, we surely know that decimal1 > decimal2
    // to get eq3, we may only lower the LHS of eq2

    // log2(10) can be approximated with 27213.235/(2^13); so the inequality log2(10) > 27213/(1<<13) holds
    // the numerator needs to be <= 32768 to avoid an int overflow; (32768 * 65535) is still below (2**31-1)

    // with bitLog(num)-1 <= log(num) < bitLog(num)
    // log2(b1)-log2(b2) > bitLog(b1)-1 - bitLog(b2)
    // so bitLog(b1)-bitLog(b2) -1 + log2(10)*(scale2-scale1) > 0 (eq3)

    int bl1 = bitLog(b1, pad);
    int bl2 = bitLog(b2, pad);

    // log of 0 is not defined, so deal with it first
    if(pad == PAD_POS && (bl1 == 0 || bl2 == 0)) {
      if(mic != null) mic.accept(Method.LOG_ZERO);
      return Integer.compare(bl1, bl2);
    }

    int bitLogDiff = bl1 - bl2;
    int scaleDiff = scale2-scale1;
    int multiplied = scaleDiff * 27213;
    int normScaleDiff = multiplied >> 13;
    int tmp = bitLogDiff + normScaleDiff;

    // the randomized test passes with tmp>0 as well;
    // however, as it is unknown whether tmp>0 is a necessary condition
    // for decimal1>decimal2, keep it safe and stick to the derived inequality
    if(tmp -1 > 0) {
      if(mic != null) mic.accept(Method.BITLEN_A);
      return pad == PAD_POS ? 1 : -1;
    }

    // switch 1 and 2: bl2-bl1 -1 + log2(10)*(scale1-scale2) > 0
    // multiply by -1: bl1-bl2 +1 + log2(10)*(scale2-scale1) < 0
    // as scale2-scale1 is positive because of the precondition,
    // the LHS gets smaller for the smaller approximation of log2(10) > 27213/(2^13)
    if(tmp + 1 < 0) {
      if(mic != null) mic.accept(Method.BITLEN_B);
      return pad == PAD_POS ? -1 : 1;
    }

    // The decimal numbers are within a binary order of magnitude,
    // so we can't deduce which one is smaller or bigger based on their bit length.
    // We could try to evaluate b1*10^scaleDiff from left to right and compare it with b2.
    // An algorithm based on schoolbook multiplication would allow us to do this,
    // however, it would be O(n^2) and quite complex.
    // Use Java's classes as they implemented optimized integer multiplication algorithms.
    if(!useFallback) {
      if(mic != null) mic.accept(Method.FALLBACK);
      return Method.FALLBACK.ordinal();
    }
    return new BigDecimal(new BigInteger(b1), scale1).compareTo(new BigDecimal(new BigInteger(b2), scale2));
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
      int actual = normalizeCompareTo(compareTo(d1, d2, null));
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

  @Test public void testBitlen() {
    check("0.31", 0, "0E+3", 1);
    //check("-240", 0, "-580", 1);
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

  @Test
  public void bitLen() {
    assertEquals(8, bitLog(FORMAT.parseHex("FF 16"), PAD_NEG));
    assertEquals(13, bitLog(FORMAT.parseHex("E9 58"), PAD_NEG));


    assertEquals(0, bitLog(FORMAT.parseHex("00 00 00"), PAD_POS));
    assertEquals(1, bitLog(FORMAT.parseHex("00 00 01"), PAD_POS));
    assertEquals(2, bitLog(FORMAT.parseHex("00 00 02"), PAD_POS));
    assertEquals(3, bitLog(FORMAT.parseHex("00 00 04"), PAD_POS));
    assertEquals(4, bitLog(FORMAT.parseHex("00 00 08"), PAD_POS));
    assertEquals(5, bitLog(FORMAT.parseHex("00 00 10"), PAD_POS));
    assertEquals(5, bitLog(FORMAT.parseHex("00 00 11"), PAD_POS));
    assertEquals(9, bitLog(FORMAT.parseHex("00 01 11"), PAD_POS));
    assertEquals(17, bitLog(FORMAT.parseHex("01 11 11"), PAD_POS));
    assertEquals(17, bitLog(FORMAT.parseHex("01 00 00"), PAD_POS));
    assertEquals(17, bitLog(FORMAT.parseHex("00 00 00 01 00 00"), PAD_POS));

    assertEquals(0, bitLog(FORMAT.parseHex("FF FF FF"), PAD_NEG));
    assertEquals(1, bitLog(FORMAT.parseHex("FF FF FE"), PAD_NEG));
    assertEquals(2, bitLog(FORMAT.parseHex("FF FF FD"), PAD_NEG));
    assertEquals(3, bitLog(FORMAT.parseHex("FF FF FB"), PAD_NEG));
    assertEquals(4, bitLog(FORMAT.parseHex("FF FF F7"), PAD_NEG));
    assertEquals(5, bitLog(FORMAT.parseHex("FF FF EF"), PAD_NEG));
    assertEquals(5, bitLog(FORMAT.parseHex("FF FF EE"), PAD_NEG));
    assertEquals(9, bitLog(FORMAT.parseHex("FF FE EE"), PAD_NEG));
    assertEquals(17, bitLog(FORMAT.parseHex("FE EE EE"), PAD_NEG));
    assertEquals(17, bitLog(FORMAT.parseHex("FE FF FF"), PAD_NEG));
    assertEquals(17, bitLog(FORMAT.parseHex("FF FF FF FE FF FF"), PAD_NEG));
  }

  @Test
  public void bitLenPostcondition() {
    for(int i=-18; i<18; i+=1) {
      if(i==0) continue;
      BigInteger x = BigInteger.valueOf(i);
      int bl = bitLog(x.toByteArray(), i < 0 ? PAD_NEG : PAD_POS);
      int log = BigIntegerMath.log2(x.abs(), RoundingMode.DOWN);
      if(log<0) fail("unexpected");

      assertEquals(log+1, bl);
    }


    for(int i=0; i<100; i++) {
      System.out.println();
      BigInteger p = BigInteger.TWO.pow(i);
      for(int neg=0; neg<2; neg++) {
        System.out.println("neg: " + (-neg));

        if(neg == 1) p = p.negate();

        for(int j=-2; j<=2; j++) {
          BigInteger x = p.add(BigInteger.valueOf(j));
          if(x.compareTo(BigInteger.ZERO) == 0) continue;
          int bl = bitLog(x.toByteArray(), x.signum() == -1 ? PAD_NEG : PAD_POS);
          int log = BigIntegerMath.log2(x.abs(), RoundingMode.DOWN);
          if(log<0) fail("unexpected");

          System.out.println(x + "  bit log " + bl + " log " + log);
          if(log+1 != bl) {
            fail(x.toString());
          }
          // check inequalities

          if(!(bl-1 <= log)) fail(x.toString());
          if(!(log < bl)) fail(x.toString());
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


  private void randomInner(long seed, int minScale, int maxScale, MethodInfoCallback mic) {
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
      int actual = compareToInner(d1, d2, false, m -> methodIdx[0] = m.ordinal());
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

package org.apache.hadoop.hive.metastore.columnstats;

import com.google.common.base.Strings;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.metastore.api.Decimal;
import org.apache.hadoop.hive.metastore.api.utils.DecimalUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.Assert.assertEquals;

public class DecimalComparisonTest {

  static final byte PAD_POS = (byte) 0;
  static final byte PAD_NEG = (byte) 255;

  public static final HexFormat FORMAT = HexFormat.ofDelimiter(" ");
  final Decimal DECIMAL_NEGA = DecimalUtils.getDecimal(-102, 1);
    final Decimal DECIMAL_NEGB = DecimalUtils.getDecimal(-1232, 1);

  public String toStr(Decimal val) {
    HiveDecimal hiveDecimal = HiveDecimal.create(new BigInteger(val.getUnscaled()), val.getScale());
    return hiveDecimal.toString();
  }

    /**
     * Note: 0 is interpreted as positive
     */
    private boolean positive(byte[] unscaled) {
      if(unscaled.length == 0) return true;
      return 0 == (unscaled[0]>>7);
    }

    public int compareTo(Decimal d1, Decimal d2) {
      byte[] b1 = d1.getUnscaled();
      byte[] b2 = d2.getUnscaled();
      boolean sign1 = positive(b1);
      boolean sign2 = positive(b2);

      System.out.println("  " + toStr(d1) + " positive " + sign1 + " / " + toStr(d2) + " positive " + sign2);
      if (sign1 != sign2) {
        return Boolean.compare(sign1, sign2);
      }

      byte pad = sign1 ? (byte)0 : (byte)0xff;
      if (d1.getScale() == d2.getScale()) {
        System.out.println("  same scale: " + d1.getScale());
        return sign1 ? compareSameScale(pad, b1, b2) : compareSameScale(pad, b2, b1);
      }

      System.out.println("  different scale, " + d1.getScale() + " vs " + d2.getScale() +" :/");

      // Hive's scale are the digits behind the dot ...
      if (d1.getScale() < d2.getScale()) {
        return compareToScaleDiff(pad, b1, d1.getScale(), b2, d2.getScale());
      }
      else {
        return -compareToScaleDiff(pad, b2, d2.getScale(), b1, d1.getScale());
      }
    }

  private static int compareSameScale(byte pad, byte[] b1, byte[] b2) {
    int len = Math.max(b1.length, b2.length);
    int i1 = b1.length-len;
    int i2 = b2.length-len;
    for(int i=0; i<len; i++) {
      // TODO: test case
      byte c1 = i1 < 0 ? pad : b1[i1];
      byte c2 = i2 < 0 ? pad : b2[i2];
      //System.out.println("cmp " + c1 + "  " + c2 );
      if(c1 != c2)
        return Byte.compareUnsigned(c1, c2);
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

  int bitLen(byte[] b, byte pad) {
      int start = findStart(b, pad);
      if(start == b.length) return 0;
      int inv = (byte) (b[start]^pad);
      return (b.length-start-1)*8 + 32-Integer.numberOfLeadingZeros(inv);
  }

  /** Precondition: scale1 < scale2 */
  private int compareToScaleDiff(byte pad, byte[] b1, short scale1, byte[] b2, short scale2) {
    // TODO: WHERE DOES THE NUMBER START?!? find start!
    int l1 = bitLen(b1, pad);
    int l2 = bitLen(b2, pad);

    System.out.println("  " + FORMAT.formatHex(b1));
    System.out.println("  " + FORMAT.formatHex(b2));

    System.out.println("  l1 " + l1 + "     b1.len " + b1.length + "   scale1 " + scale1);
    System.out.println("  l2 " + l2 + "     b2.len " + b2.length + "   scale2 " + scale2);

    int scaleDiff = scale2-scale1;
    // estimate the number of bytes if we would multiply b1 by 10^scaleDiff to make the two arrays comparable
    // log2(decimal1) = log2(b1*10^scale1) > l1-1 + log2(10)*scale1
    // log2(decimal2) = log2(b2*10^scale2) < l2+1 + log2(10)*scale2
    // if decimal1 > decimal2, or equivalently log2(decimal1) > log2(decimal2), then:
    // l1-1 - l2+1 + log2(10)*(scale1-scale2) > 0
    // log2(10) can be approximated with 27213.235/(2^13); to be safe, we take a smaller approximation
    // the numerator needs to be <= 32768 to avoid an int overflow (32768 * 65535) <= (2**31-1)
    int bitLenDiff = l1 - l2;
    int multiplied = scaleDiff * 27213;
    int normScaleDiff = multiplied >> 13;
    int tmp = bitLenDiff - 2 /*- (pad&0x1)*/ + normScaleDiff;
    String info = " scale diff: " + scaleDiff + " normalized scale diff " + normScaleDiff + "  bit len diff " + bitLenDiff + "     tmp " + tmp;
    if(tmp > 0) {
      System.out.println("  A " + info);
      return pad == PAD_POS ? 1 : -1;
    }
    // similarly for the other way around, but with a higher approximation for log2(10)/8 < 54427/(2^14)
    normScaleDiff = (multiplied + scaleDiff) >> 13;
    tmp = -bitLenDiff - 2 /*- (pad&0x1)*/ + normScaleDiff;
    info = l1 + " " + l2 + " " + scaleDiff + "     tmp " + tmp;
    if(tmp < 0) {
      System.out.println("  B " + info);
      return pad == PAD_POS ? -1 : 1;
    }

    System.out.println("  fallback");
    return new BigDecimal(new BigInteger(b1), scale1).compareTo(new BigDecimal(new BigInteger(b2), scale2));
  }

  public void check(String n1, String n2) {
      System.out.println();
      checkInner(n1, n2);
      checkInner(n2, n1);
    }

    public int normalizeCompareTo(int cmp) {
      return Integer.compare(cmp, 0);
    }

    public void checkInner(String n1, String n2) {
      BigDecimal bd1 = new BigDecimal(n1), bd2 = new BigDecimal(n2);

      Decimal d1 = createDecimal(bd1, 0);
      Decimal d2 = createDecimal(bd2, 3);

      int expected = normalizeCompareTo(bd1.compareTo(bd2));
      int actual = normalizeCompareTo(compareTo(d1, d2));
      if(expected != actual) {
        //assertEquals("compareTo result was wrong for " + n1 + " and " + n2, expected, actual);
        System.out.println("compareTo result was wrong for " + n1 + "/" + toStr(d1) + " and " + n2 + "/" + toStr(d2) + ": " + expected + ", but was " + actual);
      }
    }

  byte[] rev(byte[] b) {
    byte[] r = new byte[b.length];
    for(int i=0; i<b.length; i++) {
      r[i] = b[b.length-i-1];
    }
    return r;
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
    check("-10.2", "-123.2");
    check("-10.21232", "-123.2");
  }

  @Test
  public void test2() {
    //checkInner("10", "1");
    //checkInner("1", "10");
    checkInner("-10", "-1");
    checkInner("-1", "-10");
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
    assertEquals(0, bitLen(FORMAT.parseHex("00 00 00"), PAD_POS));
    assertEquals(1, bitLen(FORMAT.parseHex("00 00 01"), PAD_POS));
    assertEquals(2, bitLen(FORMAT.parseHex("00 00 02"), PAD_POS));
    assertEquals(3, bitLen(FORMAT.parseHex("00 00 04"), PAD_POS));
    assertEquals(4, bitLen(FORMAT.parseHex("00 00 08"), PAD_POS));
    assertEquals(5, bitLen(FORMAT.parseHex("00 00 10"), PAD_POS));
    assertEquals(5, bitLen(FORMAT.parseHex("00 00 11"), PAD_POS));
    assertEquals(9, bitLen(FORMAT.parseHex("00 01 11"), PAD_POS));
    assertEquals(17, bitLen(FORMAT.parseHex("01 11 11"), PAD_POS));
    assertEquals(17, bitLen(FORMAT.parseHex("01 00 00"), PAD_POS));
    assertEquals(17, bitLen(FORMAT.parseHex("00 00 00 01 00 00"), PAD_POS));

    assertEquals(0, bitLen(FORMAT.parseHex("FF FF FF"), PAD_NEG));
    assertEquals(1, bitLen(FORMAT.parseHex("FF FF FE"), PAD_NEG));
    assertEquals(2, bitLen(FORMAT.parseHex("FF FF FD"), PAD_NEG));
    assertEquals(3, bitLen(FORMAT.parseHex("FF FF FB"), PAD_NEG));
    assertEquals(4, bitLen(FORMAT.parseHex("FF FF F7"), PAD_NEG));
    assertEquals(5, bitLen(FORMAT.parseHex("FF FF EF"), PAD_NEG));
    assertEquals(5, bitLen(FORMAT.parseHex("FF FF EE"), PAD_NEG));
    assertEquals(9, bitLen(FORMAT.parseHex("FF FE EE"), PAD_NEG));
    assertEquals(17, bitLen(FORMAT.parseHex("FE EE EE"), PAD_NEG));
    assertEquals(17, bitLen(FORMAT.parseHex("FE FF FF"), PAD_NEG));
    assertEquals(17, bitLen(FORMAT.parseHex("FF FF FF FE FF FF"), PAD_NEG));
  }
}

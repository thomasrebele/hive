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

      if (d1.getScale() > d2.getScale()) {
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

  /** Precondition: scale1 >  scale2 */
  private int compareToScaleDiff(byte pad, byte[] b1, short scale1, byte[] b2, short scale2) {
    int i1 = findStart(b1, pad);
    int i2 = findStart(b2, pad);

    int blen1 = b1.length-i1;
    int blen2 = b2.length-i2;
    if(blen1 == 0 || blen2 == 0) {
      return Integer.compare(blen1, blen2);
    }

    int exponent = scale1-scale2;
    BigInteger pow = BigInteger.TEN.pow(exponent);
    byte[] p = pow.toByteArray();

    int maxBytes2 = p.length + blen2 + 1;

    for(int i=0; i<=Math.max(blen1, maxBytes2); i++) {

    }



    return -2;
  }

  public void check(String n1, String n2) {
      System.out.println();
      checkInner(n1, n2);
      checkInner(n2, n1);
    }

    public int normalizeCompareTo(int cmp) {
      return cmp < 0 ? -1 : cmp > 0 ? 1 : 0;
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


  private class Buffer {
    byte pad;
    byte[] data;
    int before = 0;

    void addToPos(int pos, short val) {
      short tmp = data[pos];
      tmp += (short)(val & 0xff);
      data[pos] = (byte)(tmp & 0xff);
      data[pos-1] += (byte)(tmp >> 8);
    }

    byte getPos(int pos) {
      return data[pos];
    }

  }

  private class LazyMultiplication {

    byte pad;
    byte[] factor1, factor2;
    int start1, start2;
    int end1, end2;

    int pos = 0;
    int safe = 0;
    int disp = 0;
    Buffer b = new Buffer();

    byte getNextByte() {
      if (b.data == null) {
        b.data = new byte[getLength()];
      }

      if(pos < 0 || pos >= getLength()) {
        return 0;
      }

      // calculate
      int l1 = end1-start1;
      int l2 = end2-start2;
      while(disp < l1+l2) {

        for(int i1=0; i1<l1; i1++) {
          int i2 = disp-i1;
          if(i2 < 0 || i2 >= l2) continue;

          short t = (short)(((int)factor1[start1+i1]) * ((int)factor2[start2+i2]));
          System.out.println(disp + "  " + t);
          b.addToPos(1+disp, t);

        }
        disp += 1;
      }


      return b.getPos(pos++);
    }

    int getLength() {
      return (end1-start1) + (end2-start2) + 1;
    }
  }



    @Test
    public void testMultiplication() {
      BigInteger f1 = BigInteger.valueOf(0x010203);
      BigInteger f2 = BigInteger.valueOf(0x040506);

      f1 = BigInteger.valueOf(0x1fffff);
      f2 = BigInteger.valueOf(0x1fffff);

      var m = new LazyMultiplication();
      m.factor1 = f1.toByteArray();
      m.factor2 = f2.toByteArray();
      m.start1 = 0;
      m.start2 = 0;
      m.end1 = m.factor1.length;
      m.end2 = m.factor2.length;

      byte[] result = new byte[m.getLength()];
      for(int i=0; i<result.length; i++) {
        result[i] = m.getNextByte();
      }

      byte[] expected = f1.multiply(f2).toByteArray();
      System.out.println(Strings.repeat("   ", result.length - expected.length) + FORMAT.formatHex(expected));
      System.out.println(FORMAT.formatHex(result));
    }
}

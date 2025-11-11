package org.apache.hadoop.hive.metastore.columnstats;

import org.apache.hadoop.hive.metastore.api.Decimal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.function.Consumer;

public class DecimalComparator implements Comparator<Decimal> {

  public static final byte PAD_POS = (byte) 0;
  public static final byte PAD_NEG = (byte) 255;

  public enum Method {
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

  @Override
  public int compare(Decimal o1, Decimal o2) {
    return compareToInner(o1, o2, true, null);
  }

  /**
   * Note: 0 is interpreted as positive
   */
  private boolean positive(byte[] unscaled) {
    if(unscaled.length == 0) return true;
    return 0 == (Byte.toUnsignedInt(unscaled[0]) >>7);
  }

  int compareToInner(Decimal d1, Decimal d2, boolean useFallback, MethodInfoCallback mic) {
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

  static int findStart(byte[] b, byte pad) {
    for(int i=0; i<b.length; i++) {
      if (b[i] != pad) return i;
    }
    return b.length;
  }

  static boolean isNegPowTwo(byte[] b, int start) {
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
  public static int bitLog(byte[] num, byte pad) {
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


}

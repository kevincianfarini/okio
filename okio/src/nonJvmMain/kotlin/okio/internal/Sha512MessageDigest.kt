package okio.internal

import okio.OkioMessageDigest

private val k = ulongArrayOf(
  0x428a2f98d728ae22UL, 0x7137449123ef65cdUL, 0xb5c0fbcfec4d3b2fUL, 0xe9b5dba58189dbbcUL, 0x3956c25bf348b538UL,
  0x59f111f1b605d019UL, 0x923f82a4af194f9bUL, 0xab1c5ed5da6d8118UL, 0xd807aa98a3030242UL, 0x12835b0145706fbeUL,
  0x243185be4ee4b28cUL, 0x550c7dc3d5ffb4e2UL, 0x72be5d74f27b896fUL, 0x80deb1fe3b1696b1UL, 0x9bdc06a725c71235UL,
  0xc19bf174cf692694UL, 0xe49b69c19ef14ad2UL, 0xefbe4786384f25e3UL, 0x0fc19dc68b8cd5b5UL, 0x240ca1cc77ac9c65UL,
  0x2de92c6f592b0275UL, 0x4a7484aa6ea6e483UL, 0x5cb0a9dcbd41fbd4UL, 0x76f988da831153b5UL, 0x983e5152ee66dfabUL,
  0xa831c66d2db43210UL, 0xb00327c898fb213fUL, 0xbf597fc7beef0ee4UL, 0xc6e00bf33da88fc2UL, 0xd5a79147930aa725UL,
  0x06ca6351e003826fUL, 0x142929670a0e6e70UL, 0x27b70a8546d22ffcUL, 0x2e1b21385c26c926UL, 0x4d2c6dfc5ac42aedUL,
  0x53380d139d95b3dfUL, 0x650a73548baf63deUL, 0x766a0abb3c77b2a8UL, 0x81c2c92e47edaee6UL, 0x92722c851482353bUL,
  0xa2bfe8a14cf10364UL, 0xa81a664bbc423001UL, 0xc24b8b70d0f89791UL, 0xc76c51a30654be30UL, 0xd192e819d6ef5218UL,
  0xd69906245565a910UL, 0xf40e35855771202aUL, 0x106aa07032bbd1b8UL, 0x19a4c116b8d2d0c8UL, 0x1e376c085141ab53UL,
  0x2748774cdf8eeb99UL, 0x34b0bcb5e19b48a8UL, 0x391c0cb3c5c95a63UL, 0x4ed8aa4ae3418acbUL, 0x5b9cca4f7763e373UL,
  0x682e6ff3d6b2b8a3UL, 0x748f82ee5defb2fcUL, 0x78a5636f43172f60UL, 0x84c87814a1f0ab72UL, 0x8cc702081a6439ecUL,
  0x90befffa23631e28UL, 0xa4506cebde82bde9UL, 0xbef9a3f7b2c67915UL, 0xc67178f2e372532bUL, 0xca273eceea26619cUL,
  0xd186b8c721c0c207UL, 0xeada7dd6cde0eb1eUL, 0xf57d4f7fee6ed178UL, 0x06f067aa72176fbaUL, 0x0a637dc5a2c898a6UL,
  0x113f9804bef90daeUL, 0x1b710b35131c471bUL, 0x28db77f523047d84UL, 0x32caab7b40c72493UL, 0x3c9ebe0a15c9bebcUL,
  0x431d67c49c100d4cUL, 0x4cc5d4becb3e42b6UL, 0x597f299cfc657e2aUL, 0x5fcb6fab3ad6faecUL, 0x6c44198c4a475817UL
)

internal class Sha512MessageDigest : OkioMessageDigest {

  private var messageLength: Long = 0
  private var unprocessed = byteArrayOf()
  private var currentDigest = ULongHashDigest(
    0x6a09e667f3bcc908UL,
    0xbb67ae8584caa73bUL,
    0x3c6ef372fe94f82bUL,
    0xa54ff53a5f1d36f1UL,
    0x510e527fade682d1UL,
    0x9b05688c2b3e6c1fUL,
    0x1f83d9abfb41bd6bUL,
    0x5be0cd19137e2179UL
  )

  override fun update(input: ByteArray) {
    for (chunk in (unprocessed + input).chunked(128)) {
      when (chunk.size) {
        128 -> {
          currentDigest = processChunk(chunk, currentDigest)
          messageLength += 128
        }
        else -> unprocessed = chunk
      }
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessed.size

    val finalMessage = byteArrayOf(
      *unprocessed,
      0x80.toByte(),
      *ByteArray(((112 - (finalMessageLength + 1) % 128) % 128).toInt()),
      *0L.toBigEndianByteArray(), // append 64 0 bits because SHA-512 requires message length to be a 128 bit int
      *(finalMessageLength * 8L).toBigEndianByteArray()
    )

    finalMessage.chunked(128).forEach { chunk ->
      currentDigest = processChunk(chunk, currentDigest)
    }

    return currentDigest.toByteArray()
  }

  private fun processChunk(chunk: ByteArray, currentDigest: ULongHashDigest): ULongHashDigest {
    require(chunk.size == 128)

    val w = ULongArray(80)
    chunk.chunked(8).forEachIndexed { index, bytes ->
      w[index] = bytes.toULong()
    }

    for (i in 16 until 80) {
      val s0 = (w[i - 15] rightRotate 1) xor (w[i - 15] rightRotate 8) xor (w[i - 15] shr 7)
      val s1 = (w[i - 2] rightRotate 19) xor (w[i - 2] rightRotate 61) xor (w[i - 2] shr 6)
      w[i] = w[i - 16] + s0 + w[i - 7] + s1
    }

    var (a, b, c, d, e, f, g, h) = currentDigest
    for (i in 0 until 80) {
      val s0 = (a rightRotate 28) xor (a rightRotate 34) xor (a rightRotate 39)
      val s1 = (e rightRotate 14) xor (e rightRotate 18) xor (e rightRotate 41)

      val ch = (e and f) xor (e.inv() and g)
      val maj = (a and b) xor (a and c) xor (b and c)

      val t1 = h + s1 + ch + k[i] + w[i]
      val t2 = s0 + maj

      h = g
      g = f
      f = e
      e = d + t1
      d = c
      c = b
      b = a
      a = t1 + t2
    }

    return ULongHashDigest(
      (currentDigest[0] + a),
      (currentDigest[1] + b),
      (currentDigest[2] + c),
      (currentDigest[3] + d),
      (currentDigest[4] + e),
      (currentDigest[5] + f),
      (currentDigest[6] + g),
      (currentDigest[7] + h)
    )
  }
}

private class ULongHashDigest(vararg val hashValues: ULong) {

  fun toByteArray() = ByteArray(hashValues.size * 8) { index ->
    val byteIndex = index % 8
    val hashValuesIndex = index / 8

    hashValues[hashValuesIndex].getByte(byteIndex)
  }

  operator fun get(index: Int): ULong = hashValues[index]

  operator fun component1(): ULong = hashValues[0]
  operator fun component2(): ULong = hashValues[1]
  operator fun component3(): ULong = hashValues[2]
  operator fun component4(): ULong = hashValues[3]
  operator fun component5(): ULong = hashValues[4]
  operator fun component6(): ULong = hashValues[5]
  operator fun component7(): ULong = hashValues[6]
  operator fun component8(): ULong = hashValues[7]
}
/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio.internal

import okio.Buffer

internal abstract class AbstractMessageDigest<T>(private val chunkSize: Int) : OkioMessageDigest {

  private var messageLength: Long = 0
  private val unprocessed = ByteArray(chunkSize)
  private val paddingModSize: Int = chunkSize - ((chunkSize / 64) * 8)
  private var unprocessedLimit = 0
  protected abstract val hashValues: T

  override fun update(input: ByteArray, offset: Int, limit: Int) {
    require(offset <= limit)

    var offset = offset

    if (unprocessedLimit > 0) {
      val remainingInInput = limit - offset
      val remainingInUnprocessed = chunkSize - unprocessedLimit

      if (remainingInInput < remainingInUnprocessed) {
        input.copyInto(unprocessed, unprocessedLimit, offset, offset + remainingInInput)
        unprocessedLimit += remainingInInput
        return
      } else {
        input.copyInto(unprocessed, unprocessedLimit, offset, offset + remainingInUnprocessed)
        processChunk(unprocessed, 0)
        messageLength += chunkSize
        unprocessedLimit = 0
        offset += remainingInUnprocessed
      }
    }

    while (true) {
      val remainingInInput = limit - offset

      if (remainingInInput < chunkSize) {
        input.copyInto(unprocessed, 0, offset, offset + remainingInInput)
        unprocessedLimit = remainingInInput
        return
      }

      processChunk(input, offset)
      messageLength += chunkSize
      offset += chunkSize
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessedLimit

    val finalMessage = Buffer()
      .write(unprocessed, 0, unprocessedLimit)
      .writeByte(0x80)
      .write(ByteArray((paddingModSize - (finalMessageLength + 1) absMod chunkSize).toInt()))
      .also { if (chunkSize > 64) it.writeLong(0) }
      .writeLong(finalMessageLength * 8L)
      .readByteArray()

    var offset = 0
    while (offset < finalMessage.size) {
      processChunk(finalMessage, offset)
      offset += chunkSize
    }

    return hasValuesToByteArray()
  }

  protected abstract fun hasValuesToByteArray(): ByteArray

  protected abstract fun processChunk(array: ByteArray, offset: Int)
}

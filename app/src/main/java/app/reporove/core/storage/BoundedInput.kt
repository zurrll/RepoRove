package app.reporove.core.storage

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** API 29 compatible bounded read; unlike readBytes(), untrusted streams cannot exhaust memory. */
fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 32 * 1024)); val buffer = ByteArray(8192)
    while (output.size() <= limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

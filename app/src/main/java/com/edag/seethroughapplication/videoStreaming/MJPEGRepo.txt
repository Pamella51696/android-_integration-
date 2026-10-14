package com.edag.seethroughapplication.videoStreaming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.edag.seethroughapplication.Constants
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MJPEGRepository {
    /*
        1) MJPEGRepository object acts as a simple MJPEG stream receiver.
        2) It continuously reads data from a network URL:
            - Extracts JPEG images from the stream,
            - Converts them into Android Bitmap objects,
            - And stores the most recent frame in latestBitmap.
    */

    @Volatile
    var latestBitmap: Bitmap? = null

    private var started = false

    fun start(serverIP: String) {

        if (started) return
        started = true

        Thread {

            try {

                val url =
                    URL("http://${serverIP}:9090/stitch")

                Log.d("SERVER_URL", "http://${serverIP}:9090/stitch")

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.connectTimeout = Constants.CONNECTION_TIME_OUT
                connection.readTimeout = 0

                val input = connection.inputStream

                val buffer = ByteArray(4096)

                val jpegBuffer = ByteArrayOutputStream()

                var prev = -1

                while (true) {

                    val len = input.read(buffer)

                    if (len <= 0) continue

                    for (i in 0 until len) {

                        val current = buffer[i].toInt() and 0xFF

                        // JPEG START
                        if (prev == 0xFF && current == 0xD8) {
                            jpegBuffer.reset()
                            jpegBuffer.write(0xFF)
                        }

                        if (jpegBuffer.size() > 0) {
                            jpegBuffer.write(current)
                        }

                        // JPEG END
                        if (prev == 0xFF && current == 0xD9) {

                            val bytes = jpegBuffer.toByteArray()

                            BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size
                            )?.let {
                                latestBitmap = it
                            }

                            jpegBuffer.reset()
                        }

                        prev = current
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }.start()
    }
}

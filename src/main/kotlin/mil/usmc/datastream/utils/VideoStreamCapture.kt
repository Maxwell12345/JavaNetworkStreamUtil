package mil.usmc.datastream.utils

import kotlinx.coroutines.*
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.VideoWriter
import org.opencv.videoio.Videoio
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

@Component
class VideoStreamCapture(
    // Requires default configs for Bean to work
    private var videoStreamUrl: String = "",
    private var saveFilePath: String = ""
) {
    private var videoCapture: VideoCapture? = null

    @Volatile
    var currentFrameImage: BufferedImage? = null
    private var shouldSaveToFile: Boolean = saveFilePath.isNotEmpty()

    @Volatile
    private var isMultiChannel: Boolean = false
    @Volatile
    private var isCaptureSessionActive: Boolean = false
    @Volatile
    private var isProcessingFinished: Boolean = false

    private var videoWriter: VideoWriter? = null

    private val logger: Logger = LoggerFactory.getLogger(VideoStreamCapture::class.java)


    @Async
    private fun startStreamProcessing(): Boolean {
        try {
            return if (!videoCapture!!.isOpened) {
                logger.error("Error: Stream not found.")
                false
            } else {
                val frameMat = Mat()
                while (isCaptureSessionActive) {
                    if (videoCapture!!.read(frameMat) && !frameMat.empty()) {
                        if (shouldSaveToFile) writeFrameToVideoWriter(frameMat)
                        currentFrameImage = convertMatToBufferedImage(frameMat)
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.error("An error occurred during the video streaming process: {}", e.message)
            return false
        }
    }

    @Async
    fun writeFrameToVideoWriter(frame: Mat): Boolean {
        try {
            videoWriter?.write(frame)
            return true
        } catch (e: Exception) {
            logger.error("An error occurred while writing the frame: {}", e.message)
            return false
        }
    }

    @Async
    fun getCurrentFrameAsync(): BufferedImage? {
        return currentFrameImage
    }


    private fun currentFrameMat(): Mat {
        val testFrame = Mat()
        var retryCount = 0
        while (testFrame.empty()) {
            videoCapture!!.read(testFrame)
            retryCount++
            if (retryCount > 10) {
                logger.error("Unable to retrieve a valid frame from the video/data stream.")
                break
            }
        }
        return testFrame
    }

    private fun initializeVideoWriter(): Boolean {
        val frame = currentFrameMat()

        if (!frame.empty()) {
            val channels = frame.channels()
            isMultiChannel = channels != 1

            if (channels !in 1..3) {
                logger.error("Unable to determine the channel type of the video/data stream.")
                return false
            }
        }

        try {
            videoWriter = VideoWriter(
                saveFilePath,
                VideoWriter.fourcc('M', 'J', 'P', 'G'),
                videoCapture!!.get(Videoio.CAP_PROP_FPS),
                Size(frame.width().toDouble(), frame.height().toDouble()),
                isMultiChannel
            )

            logger.info("VideoWriter initialized successfully.")
            return true
        } catch (e: Exception) {
            logger.error("An error occurred during the initialization of VideoWriter: {}", e.message)
            return false
        }

    }

    @Async
    @OptIn(DelicateCoroutinesApi::class)
    fun start(): Boolean {
        if (videoStreamUrl.isEmpty()) {
            logger.error("Udp Stream URL Not Initialized.")
            return false
        }

        GlobalScope.launch {
            videoCapture = VideoCapture(videoStreamUrl)
            if (shouldSaveToFile) initializeVideoWriter()

            isCaptureSessionActive = true
            logger.info("Video streaming process initiated.")
            startStreamProcessing()
            isProcessingFinished = true
        }
        return true
    }

    @Async
    fun stop(): Boolean {
        try {
            isCaptureSessionActive = false

            while (true) {
                if (isProcessingFinished) {
                    videoWriter?.release()
                    if (shouldSaveToFile) {
                        videoCapture?.release()
                        logger.info("Video file saved to: $saveFilePath")
                    }
                    logger.info("Video streams released successfully.")
                    break
                }
                Thread.sleep(1)
            }

            return true
        } catch (e: Exception) {
            logger.error("An error occurred while stopping the video stream: {}", e.message)
            return false
        }
    }

    fun convertMatToBufferedImage(frame: Mat): BufferedImage? {
        val channelFlag = if (isMultiChannel) BufferedImage.TYPE_3BYTE_BGR else BufferedImage.TYPE_BYTE_GRAY

        val bufferedImage = BufferedImage(frame.cols(), frame.rows(), channelFlag)
        val data = (bufferedImage.raster.dataBuffer as DataBufferByte).data
        frame[0, 0, data]

        return bufferedImage
    }
}

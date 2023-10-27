package mil.usmc.datastream

import kotlinx.coroutines.DelicateCoroutinesApi
import mil.usmc.datastream.utils.VideoStreamCapture
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableAsync
import java.util.*

@SpringBootApplication
@EnableAsync
class DatastreamApplication

// Load OpenCV library
@Bean
fun loadOpenCV() {
	// Set the path to the OpenCV library
	val opencvLibPath = System.getProperty("user.dir") + "/libs/opencv/build/lib/libopencv_java480.dylib"
	// Load the OpenCV library
	System.load(opencvLibPath)
}

/**
 * Demo to show how to use utility
 * This example shows how to run initialize and launch the async method "start"
 * In the method "start" the checks and opening of the socket occurs
 * As that function is executing, a timer is also executing for demo purposes to kill the running capture session
 * Once the "stop" method is called, all instances of the session are killed and another "start" call is required to capture more data
 */
@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
	// Call the function to load the OpenCV library
	loadOpenCV()

	// Run the Spring Boot application
	runApplication<DatastreamApplication>(*args)

	// Initialize an instance of VideoStreamCapture
	// You can also construct the VideoStreamCapture object with only the streamUrl
	// 		- That will functionally stop any writing of the frames into a file
	val cap1 = VideoStreamCapture("udp://localhost:12345", "/Users/maxwell.idler.secman/Desktop/Workspace/datastream/file_1000.avi")

	// Launch the start function asynchronously
	cap1.start()

	// Create a new instance of Timer
	val timer = Timer()

	// Define the delay in seconds for the timer
	val delayInSeconds = 5L // 10 seconds delay

	// Schedule a TimerTask that will execute after the specified delay
	timer.schedule(object : TimerTask() {
		// Override the run method to define the task to be executed
		override fun run() {
			// Call the stop function of VideoStreamCapture to stop the video capture asynchronously
			cap1.stop()

			// Cancel the Timer to stop any further scheduling
			timer.cancel()
		}
	}, delayInSeconds * 1000) // Convert the delay to milliseconds
}


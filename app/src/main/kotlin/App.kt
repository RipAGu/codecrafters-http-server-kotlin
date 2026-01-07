import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale.getDefault

const val OK = "HTTP/1.1 200 OK\r\n"
const val NOT_FOUND = "HTTP/1.1 404 Not Found\r\n\r\n"

fun main(args: Array<String>) {

    val directory = getOption(args, "--directory")

    println("Directory: $directory")
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // Uncomment this block to pass the first stage
    val serverSocket = ServerSocket(4221)
    val serverScope = CoroutineScope(Dispatchers.IO)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true


    runBlocking {
        while (true) {
            val socket = serverSocket.accept() // Wait for connection from a client.
            println("accepted new connection")

            serverScope.launch {
                try {
                    val reader = socket.inputStream.bufferedReader()
                    val requestLine = reader.readLine()


                    val (method, path, protocol) = requestLine.split(" ")
                    val headers = readHeader(reader)


                    when {
                        path == "/" -> socket.outputStream.write((OK + "\r\n").toByteArray())
                        path.startsWith("/echo") -> echo(path, socket)
                        path == "/user-agent" -> userAgent(path, socket, headers)
                        path.startsWith("/files") -> files(path, socket, directory)
                        else -> socket.outputStream.write(NOT_FOUND.toByteArray())
                    }

                } finally {
                    socket.close()
                }
            }
        }
    }

}

fun userAgent(path: String, socket: Socket, headers: Map<String, String>) {
    val header = headers["user-agent"] ?: ""
    val rawResponse = StringBuilder()
    rawResponse.append(OK)
    rawResponse.append("Content-Type: text/plain\r\n")
    rawResponse.append("Content-Length: ${header.length}\r\n")
    rawResponse.append("\r\n")
    rawResponse.append(header)

    socket.outputStream.write(rawResponse.toString().toByteArray())
}

fun echo(path: String, socket: java.net.Socket) {
    val payload = path.removePrefix("/echo/")
    val rawResponse = buildString {
        append(OK)
        append("Content-Type: text/plain\r\n")
        append("Content-Length: ${payload.length}\r\n")
        append("\r\n")
        append(payload)
    }

    socket.outputStream.write(rawResponse.toString().toByteArray())
}

fun readHeader(reader: java.io.BufferedReader): Map<String, String> {
    val headers = mutableMapOf<String, String>()

    while (true) {
        val line = reader.readLine() ?: break
        if (line.isEmpty()) break

        val idx = line.indexOf(":")
        if (idx == -1) break

        val key = line.substring(0, idx).trim().lowercase(getDefault())
        val value = line.substring(idx + 1).trim()

        headers[key] = value
    }
    return headers
}

fun getOption(args: Array<String>, name: String): String? {
    val idx = args.indexOf(name)
    return if (idx != -1) args.getOrNull(idx + 1) else null
}

fun files(path: String, socket: java.net.Socket, directory: String?) {
    if (directory == null) {
        println("Directory not found")
        socket.outputStream.write(NOT_FOUND.toByteArray())
        return
    }

    val fileName = path.removePrefix("/files/")
    if (fileName.isEmpty()) {
        println("File not found")
        socket.outputStream.write(NOT_FOUND.toByteArray())
        return
    }

    val file = File(directory, fileName)
    if (!file.exists()) {
        println("File not found")
        socket.outputStream.write(NOT_FOUND.toByteArray())
        return
    }

    val bytes = file.readBytes()

    val header = buildString {
        append(OK)
        append("Content-Type: application/octet-stream\r\n")
        append("content-length: ${bytes.size}\r\n")
        append("\r\n")
    }

    socket.outputStream.write(header.toByteArray())
    socket.outputStream.write(bytes)

}
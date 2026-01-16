import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale.getDefault

const val OK = "HTTP/1.1 200 OK\r\n"
const val NOT_FOUND = "HTTP/1.1 404 Not Found\r\n\r\n"
const val CREATED = "HTTP/1.1 201 Created\r\n\r\n"

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
                    val input = socket.getInputStream()

                    val requestLine = readLine(input)


                    val (method, path, protocol) = requestLine.split(" ")
                    println("method: $method, path: $path, protocol: $protocol")
                    val headers = readHeader(input)
                    println("headers: $headers")

                    val encodingHeader = headers["accept-encoding"]


                    when {
                        method == "GET" && path == "/" -> socket.outputStream.write((OK + "\r\n").toByteArray())
                        method == "GET" && path.startsWith("/echo") -> echo(path, socket, encodingHeader)
                        method == "GET" && path == "/user-agent" -> userAgent(path, socket, headers)
                        method == "GET" && path.startsWith("/files") -> getFiles(path, socket, directory)
                        method == "POST" && path.startsWith("/files") -> postFile(
                            directory,
                            path,
                            socket,
                            headers,
                            input
                        )

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

fun echo(path: String, socket: java.net.Socket, encodingHeader: String?) {
    val payload = path.removePrefix("/echo/")
    val rawResponse = buildString {
        append(OK)
        append("Content-Type: text/plain\r\n")
        append("Content-Length: ${payload.length}\r\n")
        if (encodingHeader != null && encodingHeader.startsWith("gzip")) {
            append("Content-Encoding: $encodingHeader")
            append("\r\n")
        }
        append("\r\n")
        append(payload)
    }



    socket.outputStream.write(rawResponse.toString().toByteArray())
}

fun readLine(inputStream: InputStream): String {
    val lineBytes = ByteArrayOutputStream()
    while (true) {
        val b = inputStream.read()
        if (b == -1) break

        if (b == '\r'.code) {
            val next = inputStream.read()
            if (next == '\n'.code) {
                break
            }
        }
        lineBytes.write(b)
    }
    return lineBytes.toByteArray().decodeToString()
}

fun readHeader(input: InputStream): Map<String, String> {
    val header = HashMap<String, String>()
    while (true) {
        val line = readLine(input)
        if (line.isEmpty()) break

        val idx = line.indexOf(":")
        if (idx == -1) break

        val key = line.substring(0, idx).trim().lowercase(getDefault())
        val value = line.substring(idx + 1).trim()

        header[key] = value
    }
    return header
}

fun getBody(header: Map<String, String>, input: InputStream): ByteArray {
    val length = header["content-length"]?.toIntOrNull() ?: -1
    if (length == -1) return ByteArray(0)

    val buffer = ByteArray(length)
    var offset = 0

    while (offset < length) {
        val byteRead = input.read(buffer, offset, length - offset)
        if (byteRead == -1) break
        offset += byteRead
    }
    return buffer
}

fun postFile(
    directory: String?,
    path: String,
    socket: java.net.Socket,
    headers: Map<String, String>,
    input: InputStream
) {
    val fileName = path.substringAfterLast("/")
    if (directory == null || fileName == "") {
        socket.getOutputStream().write(NOT_FOUND.toByteArray())
        return
    }

    val body = getBody(headers, input)

    val file = File(directory, fileName)
    file.writeBytes(body)
    socket.getOutputStream().write(CREATED.toByteArray())
}

fun getOption(args: Array<String>, name: String): String? {
    val idx = args.indexOf(name)
    return if (idx != -1) args.getOrNull(idx + 1) else null
}

fun getFiles(path: String, socket: java.net.Socket, directory: String?) {
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
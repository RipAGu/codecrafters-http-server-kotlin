import java.net.ServerSocket

const val OK = "HTTP/1.1 200 OK\r\n"
const val NOT_FOUND = "HTTP/1.1 404 Not Found\r\n\r\n"

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // Uncomment this block to pass the first stage
    val serverSocket = ServerSocket(4221)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    val socket = serverSocket.accept() // Wait for connection from a client.
    println("accepted new connection")

    val reader = socket.inputStream.bufferedReader()
    val requestLine = reader.readLine()

    val (method, path, protocol) = requestLine.split(" ")

    when {
        path == "/" -> socket.outputStream.write((OK + "\r\n").toByteArray())
        path.startsWith("/echo") -> echo(path, socket)
        else -> socket.outputStream.write(NOT_FOUND.toByteArray())
    }
}

fun echo(path: String, socket: java.net.Socket) {
    val payload = path.removePrefix("/echo/")
    val rawResponse = StringBuilder()
    rawResponse.append(OK)
    rawResponse.append("Content-Type: text/plain\r\n")
    rawResponse.append("Content-Length: ${payload.length}\r\n")
    rawResponse.append("\r\n")
    rawResponse.append(payload)

    socket.outputStream.write(rawResponse.toString().toByteArray())
}
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket;

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    var serverSocket = ServerSocket(4221)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true
    val response200 = "HTTP/1.1 200 OK\r\n"
    val response404 = "HTTP/1.1 404 Not Found\r\n\r\n"

    val socket = serverSocket.accept() // Wait for connection from client.

    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    val requestLine = reader.readLine()
    val output = socket.getOutputStream()

    val paths = requestLine.split(" ")

    val path = if (paths.size > 1) paths[1] else "/"
    val prefix = path.split("/")
    val isEcho = prefix.size > 2 && prefix[1] == "echo"

    if (path != "/" && !isEcho) {
        output.write(response404.toByteArray())
    } else if (path == "/") {
        val res = response200 + "\r\n"
        output.write(res.toByteArray())
    } else {
        val echo = if (prefix[1] == "echo") prefix[2] else ""
        val header = "Content-Type: text/plain\r\nContent-Length: ${echo.length}\r\n\r\n${echo}"

        val res = response200 + header
        output.write(res.toByteArray())

    }

    println("accepted new connection")
}

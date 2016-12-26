package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.

            // 사용자 요청 추출
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
            String firstLine = bufferedReader.readLine();
            String method = firstLine.split(" ")[0];
            String path = firstLine.split(" ")[1];

            if (method != null && method.equalsIgnoreCase("get") && ("/index.html".equals(path) || "/user/form.html".equals(path))) {
                responseForwarding(out, path);
            }

            if (method != null && method.equalsIgnoreCase("post") && path != null && path.contains("/user/create")) {
                String ContentLength = HttpRequestUtils.readUntil(bufferedReader, "Content-Length");
                int limit = Integer.parseInt(ContentLength.split(": ")[1]);

                while (!bufferedReader.readLine().equals("")) {
                }

                String formData = IOUtils.readData(bufferedReader, limit);

                Map<String, String> paramsMap = HttpRequestUtils.parseQueryString(formData);
                User user = new User(paramsMap.get("userId"),
                    paramsMap.get("password"),
                    paramsMap.get("name"),
                    paramsMap.get("email"));

                DataBase.addUser(user);
                responseRedirect(out, "/index.html");
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseForwarding(OutputStream out, String path) throws IOException {
        byte[] body = Files.readAllBytes(new File("./webapp" + path).toPath());

        DataOutputStream dos = new DataOutputStream(out);
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void responseRedirect(OutputStream out, String path) {
        DataOutputStream dos = new DataOutputStream(out);
        response302Header(dos, path);
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String path) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: "+path+"\r\n");
            dos.writeBytes("\r\n");
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}

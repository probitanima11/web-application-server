package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;
import util.ReplacingInputStream;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

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

            if (path != null && path.contains(".css")) {
                String acceptLine = HttpRequestUtils.readUntil(bufferedReader, "Accept");
                if (acceptLine.split(": ").length > 0 && acceptLine.split(": ")[1].contains("text/css")) {
                    responseCss(out, path);
                    return;
                }
            }

            if (method != null && method.equalsIgnoreCase("get")
                && ("/index.html".equals(path)
                || "/user/form.html".equals(path)
                || "/user/login_failed.html".equals(path)
                || "/user/login.html".equals(path))) {
                responseForwarding(out, path);
                return;
            }

            if (method != null && method.equalsIgnoreCase("post") && path != null && path.contains("/user/create")) {
                String contentLength = HttpRequestUtils.readUntil(bufferedReader, "Content-Length");
                int limit = Integer.parseInt(contentLength.split(": ")[1]);

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
                return;
            }

            if (method != null && method.equalsIgnoreCase("post") && path != null && path.contains("/user/login")) {
                String contentLengthLine = HttpRequestUtils.readUntil(bufferedReader, "Content-Length");
                int contentLength = Integer.parseInt(contentLengthLine.split(": ")[1]);

                while (!bufferedReader.readLine().equals("")) {
                }

                String formData = IOUtils.readData(bufferedReader, contentLength);

                Map<String, String> paramsMap = HttpRequestUtils.parseQueryString(formData);

                User user = DataBase.findUserById(paramsMap.get("userId"));

                if (user.checkPassword(paramsMap.get("password"))) {
                    responseLoginRedirect(out, "/index.html");
                } else {
                    responseForwarding(out, "/user/login_failed.html");
                }
                return;
            }

            // 사용자 목록 조회
            if (method != null && method.equalsIgnoreCase("get") && path != null && path.contains("/user/list")) {
                String cookieLine = HttpRequestUtils.readUntil(bufferedReader, "Cookie");
                Map<String, String> cookies = HttpRequestUtils.parseCookies(cookieLine.split(": ")[1]);
                boolean isLogin = Boolean.parseBoolean(cookies.get("logined"));

                if (isLogin) {
                    Collection<User> users = DataBase.findAll();

                    byte[] body = Files.readAllBytes(new File("./webapp/user/list.html").toPath());
                    byte[] search = "<%userList%>".getBytes();
                    byte[] replacement = makeTagOfUserList(users).toString().getBytes();
                    byte[] subsBody = makeSubstituteByteArray(body, search, replacement);

                    DataOutputStream dos = new DataOutputStream(out);
                    response200Header(dos, subsBody.length);
                    responseBody(dos, subsBody);
                } else {
                    responseRedirect(out, "/user/login.html");
                }
                return;
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private StringBuilder makeTagOfUserList(Collection<User> users) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (User user : users) {
			sb.append("<tr>");
			sb.append("<th scope=\"row\">"+index+"</th>");
			sb.append("<td>" + user.getUserId() + "</td>");
			sb.append("<td>" + user.getName() + "</td>");
			sb.append("<td>" + user.getEmail() + "</td>");
			sb.append("<td><a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a></td>");
			sb.append("</tr>");
			index++;
		}
        return sb;
    }

    private byte[] makeSubstituteByteArray(byte[] body, byte[] search, byte[] replacement) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(body);
        InputStream ris = new ReplacingInputStream(bis, search, replacement);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int b;
        while (-1 != (b = ris.read())) {
			bos.write(b);
		}

        return bos.toByteArray();
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

    private void responseLoginRedirect(OutputStream out, String path) {
        DataOutputStream dos = new DataOutputStream(out);
        response302LoginHeader(dos, path);
    }

    private void responseCss(OutputStream out, String path) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body = Files.readAllBytes(new File("./webapp" + path).toPath());
        response200CssHeader(dos, body.length);
        responseBody(dos, body);
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

    private void response302LoginHeader(DataOutputStream dos, String path) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Set-Cookie: logined=true\r\n");
            dos.writeBytes("Location: "+path+"\r\n");
            dos.writeBytes("\r\n");
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
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

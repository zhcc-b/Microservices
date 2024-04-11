
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.sql.*;

public class OrderService {
    private static Connection connection;
    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("Command: java OrderService <config file>");
        }
        else{
            JSONObject config = readConfig(args[0]);
            String addr = config.get("ip").toString();
            int port = Integer.parseInt(config.get("port").toString());

            HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
            // Example: Set a custom executor with a fixed-size thread pool
            server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
            server.createContext("/order", new PostHandler(config.get("us").toString(), config.get("ps").toString()));

            server.createContext("/user", new UserHandler(config.get("us").toString()));
            server.createContext("/product", new ProductHandler(config.get("ps").toString()));

            connection = DriverManager.getConnection("jdbc:sqlite:./../../src/OrderService/OrderDB.sqlite");
            initializeDatabase(connection);


            server.setExecutor(null); // creates a default executor

            server.start();

            System.out.println("OrderServer started on port " + port);
        }


    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        if (!checkTableExists(conn, "orders")) {
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE orders (" +
                        "id INTEGER PRIMARY KEY," +
                        "product_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "quantity INTEGER NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'orders' created.");
            }
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE orders1 (" +
                        "id INTEGER PRIMARY KEY," +
                        "product_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "quantity INTEGER NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'orders1' created.");
            }
        }
    }

    private static void clearTableData(Connection conn, String tableName) throws SQLException {
        String sql = "DELETE FROM " + tableName;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Data cleared from table '" + tableName + "'.");
        }
    }
    private static boolean move_table(Connection conn, String old_table, String new_table) throws SQLException {
        if (isTableEmpty(connection, old_table)) {
            // If old_table is empty, return "success" since there's nothing to move
            return true;
        }
        String moveSql = "INSERT INTO " + new_table + " SELECT * FROM " + old_table;

        try (PreparedStatement moveStatement = connection.prepareStatement(moveSql)) {
            // Execute the SQL statement to move entities
            int rowsAffected = moveStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }
    private static boolean isTableEmpty(Connection connection, String table) throws SQLException {
        // Construct SQL statement to count rows in the table
        String countSql = "SELECT COUNT(*) FROM " + table;

        try (PreparedStatement countStatement = connection.prepareStatement(countSql);
             ResultSet resultSet = countStatement.executeQuery()) {
            // Check if the count is 0 (table is empty)
            return resultSet.next() && resultSet.getInt(1) == 0;
        }
        
    }

    private static boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData dbm = conn.getMetaData();
        try (ResultSet tables = dbm.getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    public static JSONObject readConfig(String path) throws Exception{
        Object ob = new JSONParser().parse(new FileReader(path));

        JSONObject js = (JSONObject) ob;
        JSONObject orderService = (JSONObject)js.get("OrderService");
        JSONObject us = (JSONObject)js.get("UserService");
        String uString = "http://" + us.get("ip").toString() + ":" + us.get("port").toString() + "/user";
        JSONObject ps = (JSONObject) js.get("ProductService");
        String pString = "http://" + ps.get("ip").toString() + ":" + ps.get("port").toString() + "/product";
        JSONObject iscs = (JSONObject) js.get("InterServiceCommunication");
        String iString = "http://" + iscs.get("ip").toString() + ":" + iscs.get("port").toString();
        orderService.put("us", uString);
        orderService.put("ps", pString);
        orderService.put("iscs", iString);

        return orderService;
    }
    private static int getOrderAmount(){
        int orderCount = 0;
        // Prepare the SQL query to count orders
        String sql = "SELECT MAX(id) AS max_order_id FROM orders";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            // Check if any result is returned
            if (resultSet.next()) {
                // Retrieve the max order ID
                orderCount = resultSet.getInt("max_order_id");

            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Handle database-related exceptions
        }
        return orderCount;
    }

    static class PostHandler implements HttpHandler {
        private static String upath;
        private static String ppath;

        // Constructor that takes a string <UserService location> during initialization
        public PostHandler(String upath, String ppath) {
            this.upath = upath;
            this.ppath = ppath;
        }
        public void handle(HttpExchange exchange){
            JSONObject failedResponseData = new JSONObject();
            try {
                // Handle POST request for /order
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));
                    String[] keyNames = {"product_id", "user_id", "quantity"};
                    JSONObject responseData = new JSONObject();

                    int responseCode = 200;
                    if (requestData.get("command") != null){
                        String command = requestData.get("command").toString();
                        switch (command){
                            case "place order":
                                String badCreateRequest = "Bad request: ";
                                for (String keyName: keyNames){
                                    if (requestData.get(keyName) == null){
                                        responseCode = 400;
                                        badCreateRequest = badCreateRequest + "\"" + keyName + "\", ";
                                    } else {
                                        responseData.put(keyName, Integer.parseInt(requestData.get(keyName).toString()));
                                    }
                                }
                                if (responseCode == 400){
                                    badCreateRequest = badCreateRequest.substring(0, badCreateRequest.length() - 2) +
                                            " missing. " + requestData.toString();
                                    System.out.println(badCreateRequest);
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 400);
                                    exchange.close();
                                    break;
                                }
                                int quantity = Integer.parseInt(responseData.get("quantity").toString());
                                if (quantity <= 0){
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 400);
                                    exchange.close();
                                    break;
                                }
                                if (!userExist(responseData.get("user_id").toString())) {
                                    System.out.println("User does not exist");
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 404);
                                    exchange.close();
                                    break;
                                }
                                JSONObject productInfo = getProductInfo(responseData.get("product_id").toString());
                                if (productInfo.get("quantity") == null){
                                    System.out.println("Product does not exist");
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 404);
                                    exchange.close();
                                    break;
                                }
                                int productQuantity = Integer.parseInt(productInfo.get("quantity").toString());
                                if (quantity > productQuantity){
                                    failedResponseData.put("status", "Exceeded quantity limit");
                                    sendResponse(exchange, failedResponseData.toString(), 409);
                                    exchange.close();
                                    break;
                                } else{
                                    responseCode = handleCreateOrder(requestData);
                                    if (responseCode == 200){
                                        responseData.put("status", "Success");
                                        sendResponse(exchange, responseData.toString(), responseCode);
                                        exchange.close();
                                        break;
                                    } else {
                                        failedResponseData.put("status", "Invalid Request");
                                        sendResponse(exchange, failedResponseData.toString(), 400);
                                        exchange.close();
                                    }
                                }
                            case "start":
                                clearTableData(connection,"orders");
                                move_table(connection,"orders1","orders");
                                JSONObject responseData1 = new JSONObject();
                                responseData1.put("command", "restart");
                                sendResponse(exchange, responseData1.toString(), 200);
                                exchange.close();
                                break;
                            case "shutdown":
                                clearTableData(connection,"orders1");
                                move_table(connection,"orders","orders1");
                                clearTableData(connection,"orders");
                                JSONObject responseData2 = new JSONObject();
                                responseData2.put("command", "shutdown");
                                sendResponse(exchange, responseData2.toString(), 200);
                                exchange.close();
                                System.exit(1);
                                break;
                            default:
                                failedResponseData.put("status", "Invalid Request");
                                sendResponse(exchange, failedResponseData.toString(), 400);
                                exchange.close();
                        }
                    }


                } else {
                    // Send a 405 Method Not Allowed response for non-POST requests
                    System.out.println("OrderService only accept POST request");
                    failedResponseData.put("status", "Invalid Request");
                    sendResponse(exchange, failedResponseData.toString(), 405);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                failedResponseData.put("status", "Invalid Request");
                sendResponse(exchange, failedResponseData.toString(), 400);
                exchange.close();
            }
        }



        private static boolean userExist(String userId){
            boolean exist = false;
            try {
                URL url = new URL(upath + "/" + userId);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200){
                    exist = true;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            return exist;
        }
        private static JSONObject getProductInfo(String productId){
            JSONParser jsonParser = new JSONParser();
            JSONObject responseData = new JSONObject();
            try {
                URL url = new URL(ppath + "/" + productId);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {

                    responseData = (JSONObject) jsonParser.parse(new String(connection.getInputStream().readAllBytes()));
                    connection.disconnect();
                } else{
                    connection.disconnect();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            return responseData;
        }


        private static int handleCreateOrder(JSONObject requestData) throws SQLException {
            // Implement user creation logic
            int responseCode = 200;
            int orderId = getOrderAmount() + 1;
            int userId = Integer.parseInt(requestData.get("user_id").toString());
            int productId = Integer.parseInt(requestData.get("product_id").toString());
            int quantity = Integer.parseInt(requestData.get("quantity").toString());
            JSONObject productUpdate = new JSONObject();
            productUpdate.put("id", productId);
            productUpdate.put("command", "update");

            String insertQuery = "INSERT INTO orders (id, user_id, product_id, quantity) VALUES (?, ?, ?, ?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                    preparedStatement.setInt(1, orderId);
                    preparedStatement.setInt(2, userId);
                    preparedStatement.setInt(3, productId);
                    preparedStatement.setInt(4, quantity);

                    int rowsAffected = preparedStatement.executeUpdate();
                    if (rowsAffected == 1) {
                        JSONObject currentProductInfo = getProductInfo(requestData.get("product_id").toString());
                        int newQuantity = Integer.parseInt(currentProductInfo.get("quantity").toString()) - quantity;
                        productUpdate.put("quantity", newQuantity);
                        int updateResult = updateProductQuantity(productUpdate);
                        if (updateResult == 200){
                            System.out.println("Successfully create new order: " + requestData.toString());
                        }else {
                            responseCode = updateResult;
                        }
                    }
                } catch (Exception e){
                    responseCode = 400;
                    e.printStackTrace();
                    System.out.println("Failed to create order: " + requestData.toString());
                }

            return responseCode;
        }

        private static int updateProductQuantity(JSONObject updateData) {
            int responseCode = 200;

            try {
                URL url = new URL(ppath);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");

                connection.setDoOutput(true);

                OutputStream outputStream = connection.getOutputStream();
                byte[] input = updateData.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);

                responseCode = connection.getResponseCode();
                System.out.println("Response Code: " + responseCode);

            } catch (Exception e) {
                e.printStackTrace();
                responseCode = 400;
            }
            return responseCode;
        }
    }

    static class UserHandler implements HttpHandler {
        private static String upath;

        // Constructor that takes a string <UserService location> during initialization
        public UserHandler(String upath) {
            UserHandler.upath = upath;
        }
        public void handle(HttpExchange exchange){
            String failedJSON = "{}";
            // Handle post request for /user
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    URL url = new URL(upath);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setDoOutput(true);

                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));

                    OutputStream outputStream = connection.getOutputStream();
                    byte[] input = requestData.toString().getBytes(StandardCharsets.UTF_8);
                    outputStream.write(input, 0, input.length);

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                            StringBuilder responseText = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseText.append(line);
                            }
                            System.out.println("Response Data: " + responseText.toString());
                            sendResponse(exchange, responseText.toString(), responseCode);
                            connection.disconnect();
                            exchange.close();
                        } catch (IOException e) {
                            System.out.println("Cannot read response.");
                            sendResponse(exchange, failedJSON, responseCode);
                            connection.disconnect();
                            exchange.close();
                        }
                    } else{
                        System.out.println("POST request fail: " + requestData.toString());
                        sendResponse(exchange, failedJSON, responseCode);
                        connection.disconnect();
                        exchange.close();
                    }
                }
                // Handle get request for /user
                else if ("GET".equals(exchange.getRequestMethod())){
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    if (pathSegments.length >= 3){
                        if (Objects.equals(pathSegments[2], "purchased")){
                            if (pathSegments.length != 4){
                                System.out.println("Wrong purchase url: user id missing.");
                                sendResponse(exchange, failedJSON, 400);
                                exchange.close();
                            } else{
                                if (userExist(pathSegments[3])){
                                    Map<String, String> response = handleGetOrder(Integer.parseInt(pathSegments[3]));
                                    if (Objects.equals(response.get("code"), "200")){
                                        System.out.println("Successfully get user's order history.");
                                        sendResponse(exchange, response.get("data"), 200);
                                        exchange.close();
                                    } else {
                                        System.out.println("Failed to get user's order history.");
                                        sendResponse(exchange, failedJSON, Integer.parseInt(response.get("code")));
                                        exchange.close();
                                    }
                                } else {
                                    System.out.println("User does not exist.");
                                    sendResponse(exchange, failedJSON, 404);
                                    exchange.close();
                                }
                            }
                        } else{
                            String userId = pathSegments[2];
                            URL url = new URL(upath + "/" + userId);

                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                            connection.setRequestMethod("GET");

                            int responseCode = connection.getResponseCode();
                            System.out.println("Response Code: " + responseCode);

                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                                String inputLine;
                                StringBuilder response = new StringBuilder();

                                while ((inputLine = in.readLine()) != null) {
                                    response.append(inputLine);
                                }
                                in.close();

                                System.out.println("Response Data: " + response.toString());
                                sendResponse(exchange, response.toString(), responseCode);
                                connection.disconnect();
                                exchange.close();
                            } else{
                                System.out.println("GET request fail.");
                                sendResponse(exchange, failedJSON, responseCode);
                                connection.disconnect();
                                exchange.close();
                            }
                        }
                    } else {
                        System.out.println("Get request missing information.");
                        sendResponse(exchange, failedJSON, 400);
                        exchange.close();
                    }
                } else {
                    System.out.println("User only accept POST or GET.");
                    sendResponse(exchange, failedJSON, 405);
                    exchange.close();
                }
            }catch (Exception e) {
                e.printStackTrace();
                System.out.println("Unexpected error");
                sendResponse(exchange, failedJSON, 400);
                exchange.close();
            }
        }
        private static boolean userExist(String userId){
            boolean exist = false;
            try {
                URL url = new URL(upath + "/" + userId);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200){
                    exist = true;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            return exist;
        }

        private static Map<String, String> handleGetOrder(int userId) {
            // Implement user retrieval logic
            Map<String, String> response = new HashMap<>();
            JSONObject responseData = new JSONObject();
            String responseCode = "200";
            // Prepare the SQL query
            String sql = "SELECT product_id, SUM(quantity) as total_quantity FROM orders WHERE user_id = ? GROUP BY product_id";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                // Set the user ID parameter
                preparedStatement.setInt(1, userId);

                // Execute the query
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        int productId = resultSet.getInt("product_id");
                        int totalQuantity = resultSet.getInt("total_quantity");
                        responseData.put(Integer.toString(productId), totalQuantity);
                    }
                }
            } catch (Exception e) {
                responseCode = "400";
                e.printStackTrace();
            }
            response.put("data", responseData.toString());
            response.put("code", responseCode);
            return response;
        }

    }
    static class ProductHandler implements HttpHandler {
        private static String ppath;

        // Constructor that takes a string input during initialization
        public ProductHandler(String ppath) {
            ProductHandler.ppath = ppath;
        }
        public void handle(HttpExchange exchange){
            String failedJSON = "{}";
            // Handle post request for /product
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    URL url = new URL(ppath);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setDoOutput(true);

                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));

                    OutputStream outputStream = connection.getOutputStream();
                    byte[] input = requestData.toString().getBytes(StandardCharsets.UTF_8);
                    outputStream.write(input, 0, input.length);

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                            StringBuilder responseText = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseText.append(line);
                            }
                            System.out.println("Response Data: " + responseText.toString());
                            sendResponse(exchange, responseText.toString(), responseCode);
                            connection.disconnect();
                            exchange.close();
                        } catch (IOException e) {
                            System.out.println("Cannot read response.");
                            sendResponse(exchange, failedJSON, responseCode);
                            connection.disconnect();
                            exchange.close();
                        }
                    } else{
                        System.out.println("POST request fail: " + requestData.toString());
                        sendResponse(exchange, failedJSON, responseCode);
                        connection.disconnect();
                        exchange.close();
                    }
                }
                // Handle get request for /user
                else if ("GET".equals(exchange.getRequestMethod())){
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    if (pathSegments.length == 3){
                        String productId = pathSegments[2];
                        URL url = new URL(ppath + "/" + productId);

                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod("GET");

                        int responseCode = connection.getResponseCode();
                        System.out.println("Response Code: " + responseCode);
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            String inputLine;
                            StringBuilder response = new StringBuilder();

                            while ((inputLine = in.readLine()) != null) {
                                response.append(inputLine);
                            }
                            in.close();

                            System.out.println("Response Data" + response.toString());
                            sendResponse(exchange, response.toString(), responseCode);
                            connection.disconnect();
                            exchange.close();
                        } else{
                            System.out.println("GET request fail.");
                            sendResponse(exchange, failedJSON, responseCode);
                            connection.disconnect();
                            exchange.close();
                        }
                    } else{
                        System.out.println("Invalid GET url.");
                        sendResponse(exchange, failedJSON, 400);
                        exchange.close();
                    }
                } else {
                    System.out.println("Product only accept POST or GET.");
                    sendResponse(exchange, failedJSON, 405);
                    exchange.close();
                }
            }catch (Exception e) {
                e.printStackTrace();
            }

        }

    }
    private static void sendResponse(HttpExchange exchange, String response, int code){
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}

package advisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.IntStream;

public class Advisor {
    private static final String CLIENT_ID = "76292b84cd044bb2bf640bf12d114ac4";
    private static final String CLIENT_SECRET = "a8a4cf7bcf8b41f5b80646ca57125203";
    private static String authCode;
    private static String accessToken;
    private static String authPath;
    private static String apiPath;
    private static HttpServer server;

    public static void run(String authPath, String apiPath) throws IOException, InterruptedException {
        Advisor.authPath = authPath;
        Advisor.apiPath = apiPath;
        startServer();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            String command = scanner.next();

            if ("exit".equals(command)) {
                System.out.println("---GOODBYE!---");
                return;
            }

            if (accessToken == null) {
                if ("auth".equals(command)) {
                    authorize();
                } else {
                    System.out.println("Please, provide access for application.");
                }
                continue;
            }

            switch (command) {
                case "new": {
                    showNewAlbums();
                    break;
                }
                case "featured": {
                    showFeaturedPlaylists();
                    break;
                }
                case "categories": {
                    showAvailableCategories();
                    break;
                }
                case "playlists": {
                    String category = scanner.nextLine().trim();
                    showPlaylistsOfCategory(category);
                    break;
                }
            }
        }
    }

    private static void showNewAlbums() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .headers("Authorization", "Bearer " + accessToken)
                .uri(URI.create(apiPath + "/v1/browse/new-releases"))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray albums = json.get("albums").getAsJsonObject().get("items").getAsJsonArray();

        IntStream.range(0, albums.size()).forEach(albumIdx -> {
            JsonObject album = albums.get(albumIdx).getAsJsonObject();
            System.out.println(album.get("name").getAsString());
            JsonArray artists = album.get("artists").getAsJsonArray();
            List<String> artistsList = new ArrayList<>();

            IntStream.range(0, artists.size()).forEach(artistIdx -> {
                JsonObject artist = artists.get(artistIdx).getAsJsonObject();
                artistsList.add(artist.get("name").getAsString());
            });

            System.out.println("[" + String.join(", ", artistsList) + "]");
            System.out.println(album.get("external_urls").getAsJsonObject().get("spotify").getAsString());
            System.out.println();
        });
    }

    private static void showFeaturedPlaylists() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .headers("Authorization", "Bearer " + accessToken)
                .uri(URI.create(apiPath + "/v1/browse/featured-playlists"))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        showPlaylists(json);
    }

    private static void showAvailableCategories() throws IOException, InterruptedException {
        Map<String, String> mapCategoryNameToId = loadCategories();
        mapCategoryNameToId.keySet().forEach(System.out::println);
        System.out.println();
    }

    private static void showPlaylistsOfCategory(String categoryName) throws IOException, InterruptedException {
        Map<String, String> mapCategoryNameToId = loadCategories();
        String categoryId = mapCategoryNameToId.get(categoryName);

        if (categoryId == null) {
            System.out.println("Unknown category name.");
            return;
        }

        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .headers("Authorization", "Bearer " + accessToken)
                .uri(URI.create(apiPath + "/v1/browse/categories/" + categoryId + "/playlists"))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        if (json.has("error")) {
            System.out.println(json.get("error").getAsJsonObject().get("message").getAsString());
            return;
        }

        showPlaylists(json);
    }

    private static void showPlaylists(JsonObject json) {
        JsonArray playlists = json.get("playlists").getAsJsonObject().get("items").getAsJsonArray();

        IntStream.range(0, playlists.size()).forEach(albumIdx -> {
            JsonObject playlist = playlists.get(albumIdx).getAsJsonObject();
            System.out.println(playlist.get("name").getAsString());
            System.out.println(playlist.get("external_urls").getAsJsonObject().get("spotify").getAsString());
            System.out.println();
        });
    }

    private static Map<String, String> loadCategories() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .headers("Authorization", "Bearer " + accessToken)
                .uri(URI.create(apiPath + "/v1/browse/categories"))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray categories = json.get("categories").getAsJsonObject().get("items").getAsJsonArray();
        Map<String, String> mapCategoryNameToId = new LinkedHashMap<>();

        IntStream.range(0, categories.size()).forEach(albumIdx -> {
            JsonObject category = categories.get(albumIdx).getAsJsonObject();
            String id = category.get("id").getAsString();
            String name = category.get("name").getAsString();
            mapCategoryNameToId.put(name, id);
        });

        return mapCategoryNameToId;
    }

    private static void authorize() {
        System.out.println("use this link to request the access code:");
        System.out.println("" +
                authPath + "/authorize" +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=http://localhost:8080&response_type=code");
        System.out.println("waiting for code...");
    }

    private static void startServer() throws IOException {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(8080), 0);
        server.createContext("/", httpExchange -> {
            Optional<String> code = extractCode(httpExchange.getRequestURI().getQuery());

            if (code.isEmpty()) {
                sendResponseMessage(httpExchange, "Authorization code not found. Try again.");
            } else {
                authCode = code.get();
                sendResponseMessage(httpExchange, "Got the code. Return back to your program.");
                System.out.println("code received");
                try {
                    getAccessToken();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    server.stop(1);
                }

            }
        });
        server.start();
    }

    private static void sendResponseMessage(HttpExchange httpExchange, String message) throws IOException {
        httpExchange.sendResponseHeaders(200, message.length());
        httpExchange.getResponseBody().write(message.getBytes());
        httpExchange.getResponseBody().close();
    }

    private static void getAccessToken() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .headers("Content-Type", "application/x-www-form-urlencoded",
                        "Authorization", "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes()))
                .uri(URI.create(authPath + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&redirect_uri=http://localhost:8080&code=" + authCode))
                .build();
        System.out.println("making http request for access_token...");
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
        accessToken = jsonObject.get("access_token").getAsString();
        System.out.println(accessToken);
        System.out.println("Success!");
    }

    private static Optional<String> extractCode(String query) {
        Map<String, String> params = extractQueryParams(query);
        return Optional.ofNullable(params.get("code"));
    }

    private static Map<String, String> extractQueryParams(String query) {
        if (query == null) {
            return Map.of();
        }

        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");

        for (String pair : pairs) {
            String[] keyValuePair = pair.split("=");

            if (keyValuePair.length != 2) {
                throw new RuntimeException("Wrong query format");
            }

            params.put(keyValuePair[0], keyValuePair[1]);
        }

        return params;
    }
}

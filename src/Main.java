import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends Application {

    // Kontrolki dla wyboru trybu (miasto/współrzędne)
    private TextField cityField;
    private TextField latField;
    private TextField lonField;
    private VBox cityBox;
    private VBox coordsBox;

    // Checkboxy dla danych pogodowych
    private CheckBox tempCheckBox;
    private CheckBox rainCheckBox;
    private CheckBox windCheckBox;
    private CheckBox soilTempCheckBox;
    private CheckBox pressureCheckBox;

    // WebView do wyświetlania mapy
    private WebView webView;
    private WebEngine webEngine;

    // Zmienna określająca, czy wybrano tryb prognozy
    private RadioButton forecastMode;

    // Przechowywanie danych z API do wykresów
    private JSONObject lastHourlyData;
    private String lastSelectedDataTypes;

    @Override
    public void start(Stage primaryStage) {
        // Główny kontener
        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #E3F2FD;");

        // Przełącznik: miasto vs współrzędne
        Label modeLabel = new Label("Wybierz tryb:");
        modeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton cityMode = new RadioButton("Nazwa miasta");
        RadioButton coordsMode = new RadioButton("Długość i szerokość geograficzna");
        cityMode.setToggleGroup(modeGroup);
        coordsMode.setToggleGroup(modeGroup);
        cityMode.setSelected(true);
        cityMode.setStyle("-fx-text-fill: #2196F3;");
        coordsMode.setStyle("-fx-text-fill: #2196F3;");
        HBox modeBox = new HBox(10, cityMode, coordsMode);
        modeBox.setAlignment(Pos.CENTER);

        // Kontrolki dla miasta
        cityBox = new VBox(5);
        cityField = new TextField();
        cityField.setPromptText("np. Warszawa");
        cityField.setStyle("-fx-background-color: white; -fx-border-color: #2196F3; -fx-border-width: 1px; -fx-border-radius: 5px;");
        cityBox.getChildren().addAll(new Label("Wpisz nazwę miasta:"), cityField);

        // Kontrolki dla współrzędnych
        coordsBox = new VBox(5);
        latField = new TextField();
        latField.setPromptText("np. 52.2298");
        lonField = new TextField();
        lonField.setPromptText("np. 21.0118");
        latField.setStyle("-fx-background-color: white; -fx-border-color: #2196F3; -fx-border-width: 1px; -fx-border-radius: 5px;");
        lonField.setStyle("-fx-background-color: white; -fx-border-color: #2196F3; -fx-border-width: 1px; -fx-border-radius: 5px;");
        coordsBox.getChildren().addAll(
                new Label("Szerokość geograficzna:"), latField,
                new Label("Długość geograficzna:"), lonField
        );
        coordsBox.setVisible(false);

        // Logika przełączania widoczności kontrolek
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == cityMode) {
                cityBox.setVisible(true);
                coordsBox.setVisible(false);
            } else {
                cityBox.setVisible(false);
                coordsBox.setVisible(true);
            }
        });

        // Przełącznik: prognoza vs dane historyczne
        Label dataTypeLabel = new Label("Wybierz typ danych:");
        dataTypeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        ToggleGroup dataTypeGroup = new ToggleGroup();
        forecastMode = new RadioButton("Pogoda teraźniejsza");
        RadioButton historicalMode = new RadioButton("Prognoza na 7 dni");
        forecastMode.setToggleGroup(dataTypeGroup);
        historicalMode.setToggleGroup(dataTypeGroup);
        forecastMode.setSelected(true);
        forecastMode.setStyle("-fx-text-fill: #2196F3;");
        historicalMode.setStyle("-fx-text-fill: #2196F3;");
        HBox dataTypeBox = new HBox(10, forecastMode, historicalMode);
        dataTypeBox.setAlignment(Pos.CENTER);

        // Checkboxy dla danych pogodowych w poziomie
        Label dataSelectLabel = new Label("Wybierz dane pogodowe:");
        dataSelectLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        tempCheckBox = new CheckBox("Temperatura (2m)");
        rainCheckBox = new CheckBox("Opady deszczu (średnia 24h)");
        windCheckBox = new CheckBox("Prędkość wiatru");
        soilTempCheckBox = new CheckBox("Temperatura gleby");
        pressureCheckBox = new CheckBox("Ciśnienie");
        tempCheckBox.setStyle("-fx-text-fill: #2196F3;");
        rainCheckBox.setStyle("-fx-text-fill: #2196F3;");
        windCheckBox.setStyle("-fx-text-fill: #2196F3;");
        soilTempCheckBox.setStyle("-fx-text-fill: #2196F3;");
        pressureCheckBox.setStyle("-fx-text-fill: #2196F3;");
        HBox dataCheckBoxes = new HBox(10, tempCheckBox, rainCheckBox, windCheckBox, soilTempCheckBox, pressureCheckBox);
        dataCheckBoxes.setAlignment(Pos.CENTER);

        // Przycisk "Pokaż dane"
        Button showDataButton = new Button("Pokaż dane");
        showDataButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 8px 15px; -fx-border-radius: 5px; -fx-cursor: hand;");

        // WebView do wyświetlania mapy
        webView = new WebView();
        webView.setPrefSize(650, 450);
        webEngine = webView.getEngine();

        // Połączenie JavaFX z JavaScript
        webEngine.setOnAlert(event -> {
            Platform.runLater(() -> showChartWindow());
        });

        // Akcja po kliknięciu przycisku
        showDataButton.setOnAction(event -> {
            // Sprawdzenie, jakie dane wybrano
            StringBuilder selectedDataTypes = new StringBuilder();
            if (tempCheckBox.isSelected()) selectedDataTypes.append("temperature_2m,");
            if (rainCheckBox.isSelected()) selectedDataTypes.append("rain,");
            if (windCheckBox.isSelected()) selectedDataTypes.append("windspeed_10m,");
            if (soilTempCheckBox.isSelected()) selectedDataTypes.append("soil_temperature_0cm,");
            if (pressureCheckBox.isSelected()) selectedDataTypes.append("surface_pressure,");

            if (selectedDataTypes.length() == 0) {
                webView.getEngine().loadContent("<html><body><p style='color:red;'>Wybierz co najmniej jedną daną pogodową!</p></body></html>");
                return;
            }
            selectedDataTypes.setLength(selectedDataTypes.length() - 1); // Usunięcie ostatniego przecinka
            lastSelectedDataTypes = selectedDataTypes.toString();

            // Pobranie lokalizacji
            double latitude = 0, longitude = 0;
            if (cityMode.isSelected()) {
                String city = cityField.getText().trim();
                if (city.isEmpty()) {
                    webView.getEngine().loadContent("<html><body><p style='color:red;'>Proszę wpisać nazwę miasta!</p></body></html>");
                    return;
                }
                double[] coords = getCoordsFromCity(city);
                if (coords == null) {
                    webView.getEngine().loadContent("<html><body><p style='color:red;'>Nie znaleziono współrzędnych dla miasta: " + city + "</p></body></html>");
                    return;
                }
                latitude = coords[0];
                longitude = coords[1];
            } else {
                String lat = latField.getText().trim();
                String lon = lonField.getText().trim();
                try {
                    latitude = Double.parseDouble(lat);
                    longitude = Double.parseDouble(lon);
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                        webView.getEngine().loadContent("<html><body><p style='color:red;'>Nieprawidłowe współrzędne!</p></body></html>");
                        return;
                    }
                } catch (NumberFormatException e) {
                    webView.getEngine().loadContent("<html><body><p style='color:red;'>Proszę wpisać prawidłowe współrzędne!</p></body></html>");
                    return;
                }
            }

            // Typ danych (prognoza lub historyczne)
            String endpoint = forecastMode.isSelected() ? "v1/forecast" : "v1/forecast";
            String timeParam = forecastMode.isSelected() ? "&forecast_days=1" : "&forecast_days=7";

            // Pobranie danych z API
            String apiUrl = "https://api.open-meteo.com/" + endpoint + "?latitude=" + latitude + "&longitude=" + longitude +
                    "&hourly=" + selectedDataTypes.toString() + timeParam;
            String weatherData = fetchWeatherData(apiUrl, selectedDataTypes.toString(), latitude, longitude);
            if (weatherData != null) {
                webView.getEngine().loadContent(weatherData);
            } else {
                webView.getEngine().loadContent("<html><body><p style='color:red;'>Błąd pobierania danych z API!</p></body></html>");
            }
        });

        // Dodanie wszystkich elementów do głównego kontenera
        root.getChildren().addAll(
                modeLabel, modeBox, cityBox, coordsBox,
                dataTypeLabel, dataTypeBox,
                dataSelectLabel, dataCheckBoxes,
                showDataButton, webView
        );

        // Tworzenie sceny i okna
        Scene scene = new Scene(root, 700, 900);
        scene.getStylesheets().add("https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap");
        primaryStage.setTitle("Weather Application");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Metoda pobierająca współrzędne dla podanego miasta
    private double[] getCoordsFromCity(String city) {
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + city.replace(" ", "%20") + "&count=1";
            String response = fetchDataFromApi(url);
            if (response == null) {
                System.out.println("Brak odpowiedzi z API geocoding dla miasta: " + city);
                return null;
            }
            System.out.println("Odpowiedź geocoding API: " + response);

            JSONObject json = new JSONObject(response);
            if (!json.has("results")) {
                System.out.println("Brak wyników geocoding dla miasta: " + city);
                return null;
            }

            JSONArray results = json.getJSONArray("results");
            if (results.length() == 0) {
                System.out.println("Brak wyników geocoding dla miasta: " + city);
                return null;
            }

            JSONObject cityData = results.getJSONObject(0);
            double latitude = cityData.getDouble("latitude");
            double longitude = cityData.getDouble("longitude");
            System.out.println("Znaleziono współrzędne: lat=" + latitude + ", lon=" + longitude);
            return new double[]{latitude, longitude};
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania współrzędnych dla miasta: " + city);
            e.printStackTrace();
            return null;
        }
    }

    // Metoda pobierająca dane z API
    private String fetchDataFromApi(String apiUrl) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Błąd HTTP: " + responseCode);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            return response.toString();
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania danych z API: " + apiUrl);
            e.printStackTrace();
            return null;
        }
    }

    // Metoda parsująca dane pogodowe z API i generująca HTML z mapą
    private String fetchWeatherData(String apiUrl, String selectedDataTypes, double latitude, double longitude) {
        try {
            System.out.println("Wysyłanie zapytania do API: " + apiUrl);

            String response = fetchDataFromApi(apiUrl);
            if (response == null) {
                System.out.println("Brak odpowiedzi z API.");
                return null;
            }

            System.out.println("Odpowiedź API: " + response);

            JSONObject json = new JSONObject(response);
            if (!json.has("hourly")) {
                System.out.println("Brak klucza 'hourly' w odpowiedzi JSON.");
                return null;
            }

            JSONObject hourly = json.getJSONObject("hourly");
            lastHourlyData = json; // Zapisujemy cały obiekt JSON
            JSONArray time = hourly.getJSONArray("time");
            if (time.length() == 0) {
                System.out.println("Brak danych godzinowych w odpowiedzi.");
                return null;
            }

            JSONObject units = json.getJSONObject("hourly_units");

            StringBuilder weatherInfo = new StringBuilder();

            if (forecastMode.isSelected()) {
                // Pogoda teraźniejsza (dla jednego dnia)
                int selectedIndex;
                LocalDateTime currentDateTime = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                selectedIndex = 0;
                long minDiff = Long.MAX_VALUE;

                for (int i = 0; i < time.length(); i++) {
                    String timeStr = time.getString(i);
                    LocalDateTime recordDateTime = LocalDateTime.parse(timeStr, formatter);
                    long diff = Math.abs(ChronoUnit.MINUTES.between(currentDateTime, recordDateTime));
                    if (diff < minDiff) {
                        minDiff = diff;
                        selectedIndex = i;
                    }
                }

                String[] dataTypes = selectedDataTypes.split(",");
                for (String dataType : dataTypes) {
                    if (hourly.has(dataType)) {
                        JSONArray data = hourly.getJSONArray(dataType);
                        String displayName = getDisplayName(dataType);
                        String unit = units.optString(dataType, "");
                        Object value;
                        if (dataType.equals("rain")) {
                            // Obliczanie średniej z ostatnich 24 godzin dla opadów
                            int startIndex = Math.max(0, selectedIndex - 24);
                            double sum = 0;
                            int count = 0;
                            for (int i = startIndex; i <= selectedIndex; i++) {
                                if (!data.isNull(i)) {
                                    sum += data.getDouble(i);
                                    count++;
                                }
                            }
                            value = count > 0 ? sum / count : "Brak danych";
                        } else {
                            value = data.isNull(selectedIndex) ? "Brak danych" : data.get(selectedIndex);
                        }
                        String formattedValue = (value instanceof Number) ? String.format("%.1f", ((Number) value).doubleValue()) : value.toString();
                        weatherInfo.append(String.format("%s: %s %s, ", displayName, formattedValue, unit));
                    }
                }
                weatherInfo.setLength(weatherInfo.length() - 2); // Usunięcie ostatniego przecinka i spacji
                weatherInfo.append("<br><br><b>Kliknij aby zobaczyć wykres z ostatnich 24h</b>");
            } else {
                // Prognoza na 7 dni
                String[] dataTypes = selectedDataTypes.split(",");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("d.MM");

                // Grupowanie danych po dniach (24-godzinne przedziały)
                for (int day = 0; day < 7; day++) {
                    int startIndex = day * 24;
                    int endIndex = Math.min(startIndex + 24, time.length());
                    if (startIndex >= time.length()) break;

                    // Pobierz datę dla pierwszego wpisu w dniu
                    String timeStr = time.getString(startIndex);
                    LocalDateTime dayDate = LocalDateTime.parse(timeStr, formatter);
                    weatherInfo.append("<b>").append(dayDate.format(displayFormatter)).append("</b>: ");

                    // Oblicz średnie wartości dla każdego wybranego atrybutu
                    for (String dataType : dataTypes) {
                        if (hourly.has(dataType)) {
                            JSONArray data = hourly.getJSONArray(dataType);
                            String displayName = getDisplayName(dataType);
                            String unit = units.optString(dataType, "");
                            double sum = 0;
                            int count = 0;
                            for (int i = startIndex; i < endIndex; i++) {
                                if (!data.isNull(i)) {
                                    sum += data.getDouble(i);
                                    count++;
                                }
                            }
                            String formattedValue = count > 0 ? String.format("%.1f", sum / count) : "Brak danych";
                            weatherInfo.append(String.format("%s: %s %s, ", displayName, formattedValue, unit));
                        }
                    }
                    weatherInfo.setLength(weatherInfo.length() - 2); // Usunięcie ostatniego przecinka i spacji
                    weatherInfo.append("<br>");
                }
            }

            String html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Weather Map</title>\n" +
                    "    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                    "    <style>\n" +
                    "        #map { height: 450px; width: 650px; }\n" +
                    "        .custom-tooltip { background-color: #fff; border: 1px solid #ccc; padding: 5px; border-radius: 3px; font-size: 12px; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div id=\"map\"></div>\n" +
                    "    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                    "    <script>\n" +
                    "        var map = L.map('map').setView([" + latitude + ", " + longitude + "], 10);\n" +
                    "        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                    "            maxZoom: 19,\n" +
                    "            attribution: '© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors'\n" +
                    "        }).addTo(map);\n" +
                    "\n" +
                    "        var marker = L.marker([" + latitude + ", " + longitude + "])\n" +
                    "            .addTo(map)\n" +
                    "            .bindTooltip('" + weatherInfo.toString().replace("'", "\\'") + "', { className: 'custom-tooltip', direction: 'top' })\n" +
                    "            .on('click', function() {\n" +
                    "                alert('showChart');\n" +
                    "            });\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";

            return html;
        } catch (Exception e) {
            System.out.println("Błąd podczas parsowania danych pogodowych z API.");
            e.printStackTrace();
            return null;
        }
    }

    // Metoda wyświetlająca okno z wykresami
    private void showChartWindow() {
        if (lastHourlyData == null || lastSelectedDataTypes == null || lastSelectedDataTypes.isEmpty()) {
            System.out.println("Brak danych do wyświetlenia wykresów: lastHourlyData lub lastSelectedDataTypes jest null lub pusty.");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Brak danych do wyświetlenia wykresów.");
            alert.showAndWait();
            return;
        }

        Stage chartStage = new Stage();
        chartStage.setTitle("Wykresy danych pogodowych");
        VBox chartBox = new VBox(10);
        chartBox.setPadding(new Insets(10));
        chartBox.setAlignment(Pos.CENTER);

        JSONObject hourly;
        try {
            hourly = lastHourlyData.getJSONObject("hourly");
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania klucza 'hourly' z lastHourlyData.");
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd danych: brak klucza 'hourly'.");
            alert.showAndWait();
            return;
        }

        JSONArray time;
        try {
            time = hourly.getJSONArray("time");
            if (time.length() == 0) {
                System.out.println("Brak danych godzinowych w tablicy 'time'.");
                Alert alert = new Alert(Alert.AlertType.ERROR, "Brak danych godzinowych.");
                alert.showAndWait();
                return;
            }
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania tablicy 'time' z hourly.");
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd danych: brak tablicy 'time'.");
            alert.showAndWait();
            return;
        }

        int endIndex = time.length() - 1;
        int startIndex = Math.max(0, endIndex - 24); // Tylko ostatnie 24 godziny

        // Sprawdzenie i pobranie jednostek z domyślnymi wartościami
        JSONObject units = lastHourlyData.has("hourly_units") ? lastHourlyData.getJSONObject("hourly_units") : new JSONObject();
        String tempUnit = units.optString("temperature_2m", "°C");
        String rainUnit = units.optString("rain", "mm");
        String windUnit = units.optString("windspeed_10m", "m/s");
        String soilUnit = units.optString("soil_temperature_0cm", "°C");
        String pressureUnit = units.optString("surface_pressure", "hPa");

        String[] dataTypes = lastSelectedDataTypes.split(",");
        HBox firstRow = new HBox(10);
        HBox secondRow = new HBox(10);
        firstRow.setAlignment(Pos.CENTER);
        secondRow.setAlignment(Pos.CENTER);
        int chartCount = 0;

        for (String dataType : dataTypes) {
            if (!hourly.has(dataType)) continue;

            // Tworzenie wykresu
            NumberAxis xAxis = new NumberAxis();
            xAxis.setLabel("Czas");
            NumberAxis yAxis = new NumberAxis();
            String unit = getUnitForDataType(dataType, units);
            yAxis.setLabel(getDisplayName(dataType) + " (" + unit + ")");
            LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setTitle(getDisplayName(dataType));
            lineChart.setPrefWidth(300);

            // Dodawanie danych do wykresu z rzeczywistymi godzinami jako indeksy
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(getDisplayName(dataType));
            JSONArray data = hourly.getJSONArray(dataType);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime endTime;
            try {
                endTime = LocalDateTime.parse(time.getString(endIndex), formatter);
            } catch (Exception e) {
                System.out.println("Błąd podczas parsowania czasu endTime: " + time.getString(endIndex));
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd parsowania czasu.");
                alert.showAndWait();
                return;
            }

            for (int i = startIndex; i <= endIndex; i++) {
                if (!data.isNull(i)) {
                    double value = data.getDouble(i);
                    int hoursBack = endIndex - i;
                    series.getData().add(new XYChart.Data<>(hoursBack, value));
                }
            }
            lineChart.getData().add(series);

            // Ustawienie osi X z rzeczywistymi godzinami
            xAxis.setLowerBound(0);
            xAxis.setUpperBound(24);
            xAxis.setTickUnit(6);
            xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis) {
                @Override
                public String toString(Number value) {
                    LocalDateTime timeLabel = endTime.minusHours(value.intValue());
                    return timeLabel.format(DateTimeFormatter.ofPattern("HH:mm"));
                }
            });

            // Rozmieszczenie wykresów
            if (chartCount < 2) {
                firstRow.getChildren().add(lineChart);
            } else {
                secondRow.getChildren().add(lineChart);
            }
            chartCount++;
        }

        // Przycisk eksportu danych
        Button exportButton = new Button("Eksport danych");
        exportButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 5px 15px; -fx-border-radius: 5px; -fx-cursor: hand;");
        exportButton.setOnAction(e -> exportChartData());

        chartBox.getChildren().addAll(firstRow, secondRow, exportButton);

        Scene chartScene = new Scene(chartBox, 650, 650); // Zwiększona wysokość dla przycisku
        chartStage.setScene(chartScene);
        chartStage.show();
    }

    // Metoda eksportująca dane do pliku .txt
    private void exportChartData() {
        if (lastHourlyData == null || lastSelectedDataTypes == null || lastSelectedDataTypes.isEmpty()) {
            System.out.println("Brak danych do eksportu: lastHourlyData lub lastSelectedDataTypes jest null lub pusty.");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Brak danych do eksportu.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz dane jako TXT");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki tekstowe (*.txt)", "*.txt"));
        File file = fileChooser.showSaveDialog(null);

        if (file == null) {
            System.out.println("Eksport anulowany przez użytkownika.");
            return;
        }

        JSONObject hourly;
        try {
            hourly = lastHourlyData.getJSONObject("hourly");
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania klucza 'hourly' z lastHourlyData podczas eksportu.");
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd danych: brak klucza 'hourly'.");
            alert.showAndWait();
            return;
        }

        JSONArray time;
        try {
            time = hourly.getJSONArray("time");
            if (time.length() == 0) {
                System.out.println("Brak danych godzinowych w tablicy 'time' podczas eksportu.");
                Alert alert = new Alert(Alert.AlertType.ERROR, "Brak danych godzinowych.");
                alert.showAndWait();
                return;
            }
        } catch (Exception e) {
            System.out.println("Błąd podczas pobierania tablicy 'time' z hourly podczas eksportu.");
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd danych: brak tablicy 'time'.");
            alert.showAndWait();
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            int endIndex = time.length() - 1;
            int startIndex = Math.max(0, endIndex - 24);

            writer.write("Dane pogodowe (ostatnie 24 godziny)\n");
            writer.write("Czas\t");
            String[] dataTypes = lastSelectedDataTypes.split(",");
            for (String dataType : dataTypes) {
                if (hourly.has(dataType)) {
                    writer.write(getDisplayName(dataType) + "\t");
                }
            }
            writer.write("\n");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime endTime;
            try {
                endTime = LocalDateTime.parse(time.getString(endIndex), formatter);
            } catch (Exception e) {
                System.out.println("Błąd podczas parsowania czasu endTime podczas eksportu: " + time.getString(endIndex));
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd parsowania czasu podczas eksportu.");
                alert.showAndWait();
                return;
            }

            for (int i = startIndex; i <= endIndex; i++) {
                LocalDateTime timeLabel = endTime.minusHours(endIndex - i);
                writer.write(timeLabel.format(DateTimeFormatter.ofPattern("HH:mm")) + "\t");
                for (String dataType : dataTypes) {
                    if (hourly.has(dataType)) {
                        JSONArray data = hourly.getJSONArray(dataType);
                        if (!data.isNull(i)) {
                            writer.write(String.format("%.1f", data.getDouble(i)) + "\t");
                        } else {
                            writer.write("Brak danych\t");
                        }
                    }
                }
                writer.write("\n");
            }
            System.out.println("Dane zostały zapisane do: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Błąd podczas zapisu pliku: " + file.getAbsolutePath());
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd podczas zapisu pliku: " + e.getMessage());
            alert.showAndWait();
        }
    }

    // Metoda zwracająca jednostkę dla danego typu danych
    private String getUnitForDataType(String dataType, JSONObject units) {
        switch (dataType) {
            case "temperature_2m":
                return units.optString("temperature_2m", "°C");
            case "rain":
                return units.optString("rain", "mm");
            case "windspeed_10m":
                return units.optString("windspeed_10m", "m/s");
            case "soil_temperature_0cm":
                return units.optString("soil_temperature_0cm", "°C");
            case "surface_pressure":
                return units.optString("surface_pressure", "hPa");
            default:
                return "";
        }
    }

    // Metoda mapująca nazwy techniczne na czytelne
    private String getDisplayName(String dataType) {
        switch (dataType) {
            case "temperature_2m": return "Temperatura (2m)";
            case "rain": return "Opady deszczu";
            case "windspeed_10m": return "Prędkość wiatru";
            case "soil_temperature_0cm": return "Temperatura gleby";
            case "surface_pressure": return "Ciśnienie powierzchniowe";
            default: return dataType;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
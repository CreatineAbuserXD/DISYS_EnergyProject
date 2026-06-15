package at.fhtw.disys.energycommunities.gui;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import at.fhtw.disys.energycommunities.shared.model.UsageBucket;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainController {

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private Label communityDepletedLabel;

    @FXML
    private Label gridPortionLabel;

    @FXML
    private Label historicalStatusLabel;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private ComboBox<Integer> startHourCombo;

    @FXML
    private ComboBox<Integer> endHourCombo;

    @FXML
    private TableView<UsageBucket> historicalTable;

    @FXML
    private TableColumn<UsageBucket, LocalDateTime> bucketHourColumn;

    @FXML
    private TableColumn<UsageBucket, Double> communityProducedColumn;

    @FXML
    private TableColumn<UsageBucket, Double> communityUsedColumn;

    @FXML
    private TableColumn<UsageBucket, Double> gridUsedColumn;

    @FXML
    public void initialize() {
        bucketHourColumn.setCellValueFactory(new PropertyValueFactory<>("bucketHour"));
        communityProducedColumn.setCellValueFactory(new PropertyValueFactory<>("communityProduced"));
        communityUsedColumn.setCellValueFactory(new PropertyValueFactory<>("communityUsed"));
        gridUsedColumn.setCellValueFactory(new PropertyValueFactory<>("gridUsed"));

        startDatePicker.setValue(LocalDate.now().minusDays(1));
        endDatePicker.setValue(LocalDate.now());

        setRoundedCellFactory(communityProducedColumn);
        setRoundedCellFactory(communityUsedColumn);
        setRoundedCellFactory(gridUsedColumn);

        setGermanFormat(startDatePicker);
        setGermanFormat(endDatePicker);

        for (int hour = 0; hour <= 23; hour++) {
            startHourCombo.getItems().add(hour);
            endHourCombo.getItems().add(hour);
        }
        startHourCombo.setValue(0);
        endHourCombo.setValue(23);
    }

    private <T> void setRoundedCellFactory(TableColumn<T, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.3f", value));
            }
        });
    }

    private void setGermanFormat(DatePicker datePicker) {
        datePicker.setConverter(new StringConverter<>() {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d.M.yyyy");

            @Override
            public String toString(LocalDate date) {
                return date != null ? formatter.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                return (string != null && !string.isEmpty())
                        ? LocalDate.parse(string, formatter)
                        : null;
            }
        });
    }

    @FXML
    public void onSeeCurrentEnergy() {
        communityDepletedLabel.setText("Community Depleted: loading...");
        gridPortionLabel.setText("Grid Portion: loading...");

        Task<PercentageRecord> task = new Task<>() {
            @Override
            protected PercentageRecord call() throws Exception {
                return apiClient.getCurrentEnergy();
            }
        };

        task.setOnSucceeded(event -> {
            PercentageRecord currentEnergy = task.getValue();
            communityDepletedLabel.setText(String.format(
                    "Community Depleted: %.2f %%",
                    currentEnergy.getCommunityDepleted()
            ));
            gridPortionLabel.setText(String.format(
                    "Grid Portion: %.2f %%",
                    currentEnergy.getGridPortion()
            ));
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                showError("Request was interrupted.");
            } else if (exception instanceof IOException) {
                showError("REST API is not reachable. Start the rest-api module first.");
            } else {
                showError("Could not load current energy data.");
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void onLoadHistoricalEnergy() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        Integer startHour = startHourCombo.getValue();
        Integer endHour = endHourCombo.getValue();

        if (startDate == null || endDate == null || startHour == null || endHour == null) {
            historicalStatusLabel.setText("Please select a start and end date with hour.");
            return;
        }

        LocalDateTime startTime = startDate.atTime(startHour, 0);
        LocalDateTime endTime = endDate.atTime(endHour, 0);

        if (startTime.isAfter(endTime)) {
            historicalStatusLabel.setText("Start must be before end.");
            return;
        }

        historicalStatusLabel.setText("Loading historical data...");

        String start = startTime.toString();
        String end = endTime.toString();

        Task<List<UsageBucket>> task = new Task<>() {
            @Override
            protected List<UsageBucket> call() throws Exception {
                return apiClient.getHistoricalEnergy(start, end);
            }
        };

        task.setOnSucceeded(event -> {
            historicalTable.setItems(FXCollections.observableArrayList(task.getValue()));
            historicalStatusLabel.setText("Historical records loaded: " + task.getValue().size());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                historicalStatusLabel.setText("Request was interrupted.");
            } else if (exception instanceof IOException) {
                historicalStatusLabel.setText("REST API is not reachable. Start the rest-api module first.");
            } else {
                historicalStatusLabel.setText("Could not load historical energy data.");
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        communityDepletedLabel.setText("Community Depleted: -");
        gridPortionLabel.setText("Grid Portion: " + message);
    }
}

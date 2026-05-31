package at.fhtw.disys.energycommunities.gui;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import at.fhtw.disys.energycommunities.shared.model.UsageBucket;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    }

    @FXML
    public void getCurrentEnergy(ActionEvent actionEvent) {
        onSeeCurrentEnergy(actionEvent);
    }

    @FXML
    public void onSeeCurrentEnergy(ActionEvent actionEvent) {
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
    public void onLoadHistoricalEnergy(ActionEvent actionEvent) {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null || endDate == null) {
            historicalStatusLabel.setText("Please select a start and end date.");
            return;
        }

        if (startDate.isAfter(endDate)) {
            historicalStatusLabel.setText("Start date must be before end date.");
            return;
        }

        historicalStatusLabel.setText("Loading historical data...");

        String start = startDate.atStartOfDay().toString();
        String end = endDate.atTime(23, 59, 59).toString();

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

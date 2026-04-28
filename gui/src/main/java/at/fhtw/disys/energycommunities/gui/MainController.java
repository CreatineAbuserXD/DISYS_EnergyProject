package at.fhtw.disys.energycommunities.gui;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.io.IOException;

public class MainController {

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private Label communityDepletedLabel;

    @FXML
    private Label gridPortionLabel;

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
                    "Community Depleted: %.2f kWh",
                    currentEnergy.getCommunityDepleted()
            ));
            gridPortionLabel.setText(String.format(
                    "Grid Portion: %.2f kWh",
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

    private void showError(String message) {
        communityDepletedLabel.setText("Community Depleted: -");
        gridPortionLabel.setText("Grid Portion: " + message);
    }
}

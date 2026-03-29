package at.fhtw.disys.energycommunities.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GuiApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Energy Communities");
        primaryStage.setScene(new Scene(new VBox(), 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package at.fhtw.disys.energycommunities.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class GuiApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException { //wid von Java selbst aufgerufen
        FXMLLoader fxmlLoader = new FXMLLoader(GuiApplication.class.getResource("main-view.fxml"));  //lädt das "fxml also die oberfläche wie html"
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setTitle("Energy Communities");  // Stage->Fenster selbst das die Scene anzeigt, eine Scence pro Stage
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

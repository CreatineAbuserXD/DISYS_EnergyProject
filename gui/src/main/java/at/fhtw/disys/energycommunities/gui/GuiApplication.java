// --- GUI DEVELOPMENT (JavaFX) ---
// [ ] Initialize JavaFX Application & Scene
// [ ] Build Input Components (Buttons/Fields for REST API triggers)
// [ ] Implement REST API Integration (Fetching hour/historic energy data)
// [ ] Design Data Display (Tables, Labels, or Text Areas)
// [ ] Finalize Layout (Focus on simplicity and clarity)

package at.fhtw.disys.energycommunities.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class GuiApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(GuiApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setTitle("Energy Communities");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

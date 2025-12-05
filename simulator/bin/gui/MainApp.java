package gui;

//MainApp.java
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class MainApp extends Application {
 @Override
 public void start(Stage primaryStage) {
     BorderPane root = new BorderPane();
     root.setCenter(new Label("Tomasulo Simulator - WIP"));

     Scene scene = new Scene(root, 1000, 700);
     primaryStage.setTitle("Tomasulo MIPS Simulator");
     primaryStage.setScene(scene);
     primaryStage.show();
 }

 public static void main(String[] args) {
     launch(args);
 }
}

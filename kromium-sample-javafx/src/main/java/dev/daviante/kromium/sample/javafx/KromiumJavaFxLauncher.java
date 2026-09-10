package dev.daviante.kromium.sample.javafx;

import javafx.application.Application;

public class KromiumJavaFxLauncher {
    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Kromium JavaFX");
        Application.launch(KromiumJavaFxApp.class, args);
    }
}
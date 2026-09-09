package dev.daviante.kromium.sample.javafx;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;

public class KromiumJavaFxApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        // 1. Navigation Toolbar
        Button backBtn = new Button("Back");
        Button fwdBtn = new Button("Forward");
        Button reloadBtn = new Button("Reload");
        TextField addressBar = new TextField("https://github.com/daviantegroup/kromium");
        HBox.setHgrow(addressBar, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(backBtn, fwdBtn, reloadBtn, addressBar);
        root.setTop(toolBar);

        // 2. SwingNode bridge for lightweight OSR panel
        SwingNode swingNode = new SwingNode();
        root.setCenter(swingNode);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Kromium JavaFX OSR Bridged Sample");
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> {
            Kromium.dispose();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();

        // 3. Configure Kromium for Lightweight OSR rendering
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(true) // Force OSR
                .build();

        // 4. Initialize engine
        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://github.com/daviantegroup/kromium", true, true);
                    JComponent uiComp = (JComponent) browser.getUiComponent();

                    // CRITICAL: SwingNode.setContent must run on JavaFX Application Thread
                    Platform.runLater(() -> {
                        swingNode.setContent(uiComp);

                        // Dynamically propagate JavaFX Scene resize events to Chromium OSR viewport
                        scene.widthProperty().addListener((obs, oldW, newW) -> updateBrowserSize(browser, uiComp, scene, toolBar));
                        scene.heightProperty().addListener((obs, oldH, newH) -> updateBrowserSize(browser, uiComp, scene, toolBar));
                    });

                    // Initialize OSR component and trigger native browser creation on Swing EDT
                    SwingUtilities.invokeLater(() -> {
                        int initialW = scene.getWidth() > 100 ? (int) scene.getWidth() : 1200;
                        int initialH = scene.getHeight() > 100 ? Math.max(100, (int) (scene.getHeight() - toolBar.getHeight())) : 750;

                        uiComp.setPreferredSize(new Dimension(initialW, initialH));
                        uiComp.setSize(initialW, initialH);

                        browser.getRawBrowser().createImmediately();
                        browser.getRawBrowser().wasResized(initialW, initialH);
                    });

                    // Navigation actions
                    backBtn.setOnAction(e -> browser.goBack());
                    fwdBtn.setOnAction(e -> browser.goForward());
                    reloadBtn.setOnAction(e -> browser.reload());
                    addressBar.setOnAction(e -> browser.loadUrl(addressBar.getText()));

                    // State updates
                    browser.onAddressChanged(url -> {
                        Platform.runLater(() -> addressBar.setText(url));
                    });
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private static void updateBrowserSize(KromiumBrowser browser, JComponent uiComp, Scene scene, ToolBar toolBar) {
        int w = (int) scene.getWidth();
        int h = (int) (scene.getHeight() - toolBar.getHeight());
        if (w > 50 && h > 50) {
            SwingUtilities.invokeLater(() -> {
                uiComp.setPreferredSize(new Dimension(w, h));
                uiComp.setSize(w, h);
                browser.getRawBrowser().wasResized(w, h);
            });
        }
    }
}
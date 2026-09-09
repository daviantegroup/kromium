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
import java.awt.Image;
import java.awt.Taskbar;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class KromiumJavaFxApp extends Application {

    public static final String APP_NAME = "Kromium JavaFX";

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
        primaryStage.setTitle("Kromium - JavaFX Browser");
        primaryStage.setScene(scene);

        // Set application icons
        try (InputStream is = KromiumJavaFxApp.class.getResourceAsStream("/icon.png")) {
            if (is != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(is));
            }
        } catch (Exception ignored) {}

        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    try (InputStream is = KromiumJavaFxApp.class.getResourceAsStream("/icon.png")) {
                        if (is != null) {
                            Image awtImg = ImageIO.read(is);
                            if (awtImg != null) {
                                taskbar.setIconImage(awtImg);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

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

                    Platform.runLater(() -> {
                        swingNode.setFocusTraversable(true);
                        swingNode.setContent(uiComp);

                        // Ensure clicking the browser transfers JavaFX scene focus to swingNode
                        swingNode.setOnMousePressed(e -> swingNode.requestFocus());
                        swingNode.setOnMouseClicked(e -> swingNode.requestFocus());

                        // Synchronize JavaFX focus with Chromium OSR engine
                        swingNode.focusedProperty().addListener((obs, oldVal, isFocused) -> {
                            SwingUtilities.invokeLater(() -> {
                                if (isFocused) {
                                    uiComp.requestFocusInWindow();
                                }
                                browser.getRawBrowser().setFocus(isFocused);
                            });
                        });

                        // Dynamically propagate JavaFX Scene resize events to both SwingNode and Chromium OSR viewport
                        scene.widthProperty().addListener((obs, oldW, newW) -> updateBrowserSize(browser, uiComp, swingNode, scene, toolBar));
                        scene.heightProperty().addListener((obs, oldH, newH) -> updateBrowserSize(browser, uiComp, swingNode, scene, toolBar));

                        updateBrowserSize(browser, uiComp, swingNode, scene, toolBar);
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

    private static void updateBrowserSize(KromiumBrowser browser, JComponent uiComp, SwingNode swingNode, Scene scene, ToolBar toolBar) {
        int w = (int) scene.getWidth();
        int h = (int) (scene.getHeight() - toolBar.getHeight());
        if (w > 50 && h > 50) {
            // Update JavaFX SwingNode layout bounds so hit-testing / mouse picking covers the browser view
            swingNode.resize(w, h);

            SwingUtilities.invokeLater(() -> {
                uiComp.setPreferredSize(new Dimension(w, h));
                uiComp.setSize(w, h);
                browser.getRawBrowser().createImmediately();
                browser.getRawBrowser().wasResized(w, h);
            });
        }
    }
}
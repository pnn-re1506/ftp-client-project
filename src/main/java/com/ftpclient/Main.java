package com.ftpclient;

import javax.swing.SwingUtilities;

/**
 * Entry point for the FTP Client application.
 * Launches the Swing GUI on the Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        // Swing UI must be created on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new MainWindow().setVisible(true);
            }
        });
    }
}

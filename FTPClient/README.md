# FTP Client - Java Socket Programming Project

A simple FTP client application built with Java using only standard libraries (`java.io`, `java.net`, `java.util`, `javax.swing`). No third-party dependencies. Built with Maven.

## Project Structure

```
FTPClient/
├── pom.xml
├── src/main/java/
│   ├── FTPClient.java        # High-level FTP operations
│   ├── FTPConnection.java    # Socket + control connection management
│   ├── FTPResponse.java      # FTP response parser
│   ├── FTPException.java     # Custom exception for FTP errors
│   ├── MainFrame.java        # Main Swing window
│   ├── LoginPanel.java       # Connection settings panel
│   ├── LocalPanel.java       # Local file browser
│   ├── RemotePanel.java      # Remote file browser
│   └── LogPanel.java         # FTP log
└── README.md
```

## How to Build

```bash
mvn clean package
```

## How to Run

```bash
java -jar target/ftp-client-1.0.jar
```

## Quick Start

1. Launch the application
2. Enter host, username, password
3. Click **Connect**
4. Browse remote directories by double-clicking folders
5. Download files by selecting a file and clicking **Download**
6. View all FTP commands and responses in the Log panel
7. Click **Disconnect** to disconnect
8. Close the application

## Requirements

- Java 8 or higher (JDK)
- Maven 3.x

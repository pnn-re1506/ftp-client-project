# FTP Client

A simple FTP Client application built using Java and Swing.

## Prerequisites
- **Java Development Kit (JDK):** Version 17 or higher.
- **Apache Maven:** For project management and build.

## How to Run

There are two primary ways to compile and run this application:

### Option 1: Run directly via Maven (Recommended for Development)

Open a terminal, command prompt, or PowerShell at the root directory of the project (where the `pom.xml` file is located) and execute the following commands:

```bash
# Compile the project
mvn clean compile

# Run the application
mvn exec:java -Dexec.mainClass="com.ftpclient.Main"
```

### Option 2: Build a JAR file and Execute (Recommended for End-users)

1. Open a terminal at the root directory of the project.
2. Run the following command to package the project into an executable JAR file:

```bash
mvn clean package
```

3. Once the build is successful, the JAR file will be generated in the `target` directory. You can run the application using:

```bash
java -jar target/ftp-client-1.0-SNAPSHOT.jar
```

## Main Features
*(You can list the application's features here once they are implemented, e.g., Connect to FTP Server, Upload/Download files, Directory management, etc.)*

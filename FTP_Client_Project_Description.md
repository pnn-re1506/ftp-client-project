# Bachelor Project: Build an FTP Client in Java

**Goal:**
Implement a FTP Client using Java that can interact with a public FTP server (e.g., ftp.gnu.org).

## 1. Allowed Libraries (Strict Restriction)

Only the following Java packages are allowed:

- `java.io.*`
- `java.net.*`
- `java.util.*`

No third-party libraries or additional Java packages are permitted.

## 2. Required Features

- Connect to FTP server (default port 21)
- Anonymous login and custom username/password login
- `pwd` (PWD command)
- `cd` (CWD command)
- `ls` using PASV + LIST
- `get` using PASV + RETR (binary mode)
- `put` using PASV + STOR (binary mode)
- `delete` (DELE command)
- `mkdir` (MKD command)
- `rmdir` (RMD command)
- `quit` (QUIT command)

## 3. Technical Requirements

- Must correctly handle control connection and separate data connection.
- Must correctly parse FTP reply codes, including multiline responses.
- Must properly close sockets and streams.
- Must handle errors gracefully.

## 4. Submission

1. Source code (`.java` files)
2. README with build/run instructions
3. Short report (3–8 pages) explaining architecture and protocol flow, showing test commands

**You must submit a zip file including the 3 above files into the Moodle website.**

## 5. Grading Rubric (100 Points)

| Feature | Points |
|---|---|
| Open/Connect | 5 |
| Login (USER/PASS) | 10 |
| PWD + CWD | 10 |
| PASV + LIST | 10 |
| Download (RETR) | 10 |
| Upload (STOR) | 10 |
| Simple Graphic User Interface (GUI) | 10 |
| Remote file operations | 5 |
| Response parsing (including multiline) | 8 |
| Resource management | 6 |
| Error handling & robustness | 6 |
| Documentation quality | 6 |
| Code structure & clarity | 4 |
| **Total** | **100** |

> **Note:** If you implement the GUI function, you may use two extra libraries: **Swing** and **JavaFX**.

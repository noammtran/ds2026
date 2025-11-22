# Labwork 1 - Java CLI file transfer (1:1)

Minimal Java version using raw TCP sockets with a tiny header:

- Header: `int` (4 bytes) filename length, then `long` (8 bytes) file size, then UTF-8 filename bytes, followed by file content.
- One server accepts one client, stores the file in an output directory.

## Build

```bash
javac lab1_file/Server.java lab1_file/Client.java
```

## Run

In one terminal start the server (optional args: host port output_dir):
```bash
java -cp lab1_file Server 0.0.0.0 9000 received_files
```

From another terminal send a file (args: file_path [host] [port] [remote_name]):
```bash
java -cp lab1_file Client /path/to/file 127.0.0.1 9000
```
Add `remote_name` to override the saved filename if needed.

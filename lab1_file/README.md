# Labwork 1 - Java CLI file transfer (1:1)

Minimal Java version using raw TCP sockets with a tiny header:

- Upload: send `byte 'U'`, then `int` filename length, `long` file size, UTF-8 filename bytes, then file content.
- Download: send `byte 'D'`, then `int` filename length, UTF-8 filename bytes. Server replies with `byte status` (0 ok, 1 not found); if ok, it sends `long` size + file content.
- One server accepts one client per run and stores files in the output directory.

## Build

```bash
javac lab1_file/Server.java lab1_file/Client.java
```

## Run

Start the server (optional args: host port output_dir):
```bash
java -cp lab1_file Server 0.0.0.0 9000 received_files
```

Upload from another terminal (args: file_path [host] [port] [remote_name]):
```bash
java -cp lab1_file Client upload /path/to/file 127.0.0.1 9000
```
You can omit the word `upload` (backward compatible).

Download a file saved on the server (args: remote_name [host] [port] [output_path]):
```bash
java -cp lab1_file Client download test.txt 127.0.0.1 9000 downloads/test.txt
```

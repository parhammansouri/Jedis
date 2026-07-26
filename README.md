# Tamrin 5

Java implementations for the main parts of `تمرین پنجم.pdf`.

## Build

```bat
build.bat
```

## Run

```bat
run-jedis.bat 6379
run-logrelay.bat
run.bat server 8080
run.bat client 127.0.0.1 8080
```

The thread-pool classes are available in the default package:

- `MiniThreadPool`
- `TaskQueue`
- `SimpleFuture`
- `RejectedTaskException`

`LogRelayAnalyzer` listens on port `38291` and relays logs to `localhost:38292`.

The messenger part is implemented as:

- `server.RelayServer`
- `client.RelayClient`
- `common.Frame`

Runtime data is stored under `profiles/` and `offline_data/`.

# Jedis

## Build

```bat
build.bat
```

## Run

```bat
run.bat 6379
```

or:

```bat
java -cp out Jedis 6379
```

`Jedis` is a small TCP key-value server with a line-based protocol.

Supported commands:

- `SET <key> <value>` -> `OK`
- `GET <key>` -> stored value or `NULL`
- `DEL <key>` -> `1` or `0`
- `PING` -> `PONG`

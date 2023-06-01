# Blockchain-Supported Open Goods Market

- Simple market application logic example.
- Using custom blockchain design.
- Using [Practical Byzantine Fault Tolerance (PBFT)](https://www.usenix.org/legacy/publications/library/proceedings/osdi99/full_papers/castro/castro.ps) as consensus algorithm.

## Build
### Maven
- Java 17
``` shell
mvn clean package
```
jar files will be placed in the ``deploy`` folders of the submodules ``server`` and ``client``.

## Run
- Set configurations in files ``server/deploy`` and ``client/deploy`` (defaults provided).
- Must have ``Docker``and ``Python``.
### Single Test
```shell
python3 scripts/run-open_goods.py -h
```
Provides runtime arguments descriptions.
### Multiple Tests
```shell
python3 scripts/run-experiments.py
```

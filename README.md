# Blockchain-Supported Applications

- Implemented toy Applications:
    - Open Goods Market application with Digital Cash.
    - Toolbox application to create polls with Differential Privacy
- Using simple custom blockchain design.
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
python3 scripts/run-toolbox.py -h
```
Provide runtime arguments descriptions.
### Multiple Tests
```shell
python3 scripts/run-experiments-open_goods.py
python3 scripts/run-experiments-toolbox.py
```

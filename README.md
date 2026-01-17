# Distributed Sharded E-Wallet System

## Compilation and Execution Instructions

### Prerequisites
The system was developed and tested using the following environment:

- Java 21
- Apache Maven 3.9+
- Apache ZooKeeper 3.7.x
- etcd v3.5.x
- Windows 11

ZooKeeper and etcd must be running before starting the wallet servers.

### 1. Start ZooKeeper
```powershell
cd C:\apache-zookeeper-3.7.1-bin
.\bin\zkServer.cmd
```

ZooKeeper will start and listen on port 2181.

### 2. Start etcd
```powershell
cd C:\etcd-v3.5.15-windows-amd64
.\etcd.exe
```

etcd will start and listen on port 2379.

### 3. Compile the project
```powershell
mvn -U clean compile
```

### 4. Start wallet servers
The system is configured with two partitions, each replicated across three server instances.

#### Partition 0
```powershell
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=server 50051 r1 0'
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=server 50052 r2 0'
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=server 50053 r3 0'
```

#### Partition 1
```powershell
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=server 50061 r4 1'
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=server 50062 r5 1'
mvn exec:java '-Dexec.args=server 50063 r6 1' -Dexec.mainClass=uk.ac.westminster.ds.Main
```

### 5. Verify service registration
```powershell
cd C:\etcd-v3.5.15-windows-amd64
$env:ETCDCTL_API=3
.\etcdctl.exe get "WalletService" --prefix
```

### 6. Client operations

#### Account creation
```powershell
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=client alice'
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=client bob'
```

#### Cross-partition transfer
```powershell
mvn exec:java '-Dexec.mainClass=uk.ac.westminster.ds.Main' '-Dexec.args=client transfer alice bob 20'
```

## Known Limitations

- Account data is stored in memory and is lost when all replicas are restarted.
- Account uniqueness across partitions is enforced through deterministic client-side routing.
- No persistent storage or authentication is implemented.

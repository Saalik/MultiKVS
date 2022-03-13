# System model
- Infinite persistent memory
- Single database (Db)
- Single Shard (S)
- Multi-version, supporting concurrent versions.  A version is the mapping $(key, timestamp) \rightarrow value$
- A timestamp is a scalar in a total-ordered isolation model (e.g., serialisable), or a vector in a partial-ordered isolation model (e.g., TCC).

# Snapshot

A transaction reads from a snapshot.  A snapshot is a set of versions.  For any key, it maps the *start/dependency timestamp* of the transaction to the version of that key that is visible at that timestamp.  The *visibility* relation is specific to an isolation level, and will be defined hereafter.

The snapshot of some transaction $t$ is the mapping $key \rightarrow version$

.  , i.e., it is the mapping $(key, t.start) \rightarrow value$ where $value$ is XXXXX

# Start Transaction

`beginTransaction(start)`

When a client starts a transaction at the database Db. 
BeginTransaction creates a transaction object which has the transaction snapshot as an attribute.
Transaction object attributes are: a unique identifier, dependency snapshot, CommitTimestamp, initialized at +$\infty$, buffer of pending updates.

## Preconditions
- The snapshot is valid in regards to the consistency model.

## Returns

The transaction object.


# Read

`read(Transaction, Key)`

## Preconditions
- The transaction is active.
- The caller is in the transaction.

## Returns

The value of the key in the buffer if it exist, otherwise returns the value in the snapshot.


# Effector

`Effector(Transaction, Key, Operation)`

The update create a version of the object Key and is added to the buffer of the transaction object.
<!-- , the CommitTimestamp in the Transaction Object is updated to the current timestamp. -->
An operation record is written in the journal with the following information:
CommitTimestamp, TransactionID, Type, Key, Operation, SnapshotTime.

## Preconditions
- The transaction is active.
- The caller is in the transaction.  

## Returns

Nothing

# Abort Transaction

`AbortTransaction(Transaction)`

Nothing happens because as long as the Timestamp attribute in the transaction object remains $+\infty$ the effects of the transaction will remain invisible.

<!-- An abort record is written in the journal with the following information: Timestamp,TransactionID. 
The transaction objet is deleted. -->

## Precondition

- The transaction is active.

## Returns

Nothing

# Commit Transaction

`CommitTransaction(Transaction)`

The CommitTimestamp in the Transaction Object is updated to the current timestamp.
<!-- A commit record is written in the journal with the following information: CommitTimestamp, TransactionID.
The transaction objet is deleted. -->

## Preconditions
- The transaction is active.
- The caller is in the transaction.
- There is at least one operation in the transaction
- The effects of the transaction are respect the consistency model 

## Returns

Nothing

## Postconditions

- Every object version created in this transaction have a commit timestamp.



## Visibility of updates

The updates of a transaction are visible by other transactions if the transaction has a CommitTimestamp lower than $+\infty$.


# Timestamp and transaction properties

All committed transactions have a unique timestamp.

All new versions in a transaction have a transaction-local sequence number that is monotonically increasing.

Each version in the buffer is identified by:
- Sequence Number
- CommitTimestamp

If an object is updated multiple times in a transaction. 
The version with the highest Sequence Number is the only one visible.

## General rule

For any transaction T, its commit timestamp must be higher than its dependency snapshot.

# Serializable


## Begin

For any transaction T, $T.dependency$ must be higher or equal than the commit timestamp of any committed transaction.

$\forall T' \in t_{i}, T.dependency \geq T'.commit$

## Commit

A transaction $T$ cannot commit if there exist a transaction $T'$ in $t_{i}$, with $T'.commit > T.dependency$  

$\forall T' \in t_{i},\: T.dependency \geq T'.commit$


<!-- This rule prevents concurrent transaction as any active transaction has a commit timestamp that is +inf -->

For any two committed transaction T1 and T2:

$T1.commit \leq T2.dependency \: \lor \: T2.commit \leq T1.dependency$


# Strict Serilizability

Transactions are serializable and timestamps are totally ordered and in an order that is consistent with real time.


## First solution blocks concurrent transactions.

`Begin - Precondition:`

A transaction $T$ can start if there are no running transactions.

$\nexists T' \in t_{i} \to T'.commit = +\infty$ 


## Second solution allows concurrent transactions 

`Commit - Precondition:`

A transaction $T$ can commit if there is no transaction $T'$ with a $T'.commit > T.dependency$.

$\nexists T' \in t_{i} \to T'.commit > T.dependency$


## Snapshot Isolation

`Begin`

For any transaction T, T's snapshot is composed of the transactions that have a commit timestamp lower or equal than T.s.

<!-- $\forall T' \in t_{i} \to T.c \leq$ Ti.s -->

`Commit`

A transaction $T$ can commit is there is no committed transaction $T'$ with a $T'.commit > T.dependency$ with an overlaping EffectMap.

`Precondition`

$\nexists T' \in t_{i} \to T'.Boolean=True \land T'.commit > T.dependency \land dom(T'.EffectMap) \cap dom(T.EffectMap) \neq \emptyset$

There is no writeset overlap with a committed transaction that has a higher commit TimeStamp than the dependency.


# Comments

Writing constraints about Snapshot is hard. 
The constraints on Snapshot are mainly about the Snapshot TimeStamp and its relationship with TimeStamps of transaction included in the Snapshot.

There should be something like:

$Dependency : TimeStamp \to Snapshot$

Adding a new general rule that states that a Snapshot includes all the transaction $T$, $Dependency \geq T.commit$

In this system model, when trying to read $x$ the Snapshot always return the Version (TransactionID) of the included transaction that has the highest commit TimeStamp with an EffectMap containing $x$.

In the specification above I didn't use the boolean of a transaction to check if it's committed shoudl changed.

<!-- <!-- 

, and there is no committed transaction with a higher timestamp than the dependency snapshot.

For a transaction T, its dependency snapshot must be committed, and there is no committed transaction with a higher timestamp than the dependency snapshot.

When transaction T, commits, there must be no transaction committed between dependency and the commit timestamp that has a 


A dependency snapshot must be committed,  —->  -->

# Sessions

A Session is a set of transaction made by the same client. 

## Start Session

*beginSession(userID): SessionID*

When a session starts the database assigns to the Session a Snapshot of the database creating the following relations:

Session -> SessionID

SessionID -> Snapshot

Session:

- Session ID
- Snapshot
- UserID

Session is represented by a SessionID and the sessionID is associated to a Snapshot. Multiple sessionID can be link to a single Snapshot. A snapshot is only visible to a transaction that has a SessionID associated to a Snapshot of the database. An ongoing Session is called a Live Session (LS) and a committed session a Committed Session (CS).

In a Live Session the committed updates are only visible to the client the session is originating from. When a session is committed the updates are made visible to all the clients in the database.

## End Session

*commitSession(SessionID):void*

When a client issues a commitSession the database ensures that all the transaction that are part of the session are committed. If a transaction is still running the transaction is commited. 

When a Session is committed all the transactions in the Session are merged to the database.

## Abort Session

*abortSession(SessionID):void*

There are two possibilities in case of an abort:

- All the transactions that are in the session are aborted. This solution implies that a new mechanism is created in the transaction in order to abort the committed transaction. Either by not persisting the effect of running transactions until a Session is commited therefore changing the guarantees offered by committing a transaction. Or by adding the possibility of writing an abort message for a transaction that is already running. The latter implies change to the way the recovery works.
- Only the running transaction is aborted. All the committed transaction are made available to other clients in the database. This keeps the guarantees offered by transactions and requires less changes to the overall work done until now. 

## Invariant

In a Live Session only on transaction can remain active, meaning non commited. 

If a client issues a Begin transaction while having a running transaction, either the running transaction is rejected or the running transaction is committed if the write-set is not empty.

A Live Session cannot be committed if a transaction is running.

All transactions in a Session have the Snapshot of the Session.

## Transactions in a Session

All the transaction in a Session have the same snapshot. A running transaction in a Session can read all the updates that are part of the snapshot and the updates of the committed transactions that have the same SessionID.

Because a Session can only have a single running transaction, the Read-Pool<sub>Tr<sub>x</sub></sub> (the set of updates available to a given transaction) is composed of the updates of the Snapshot of the Session and the updates of the transactions that have the same SessionID and a transactionID lower than *x*.
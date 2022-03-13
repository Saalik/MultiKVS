# Draft Papier

## Abstract
Modern databases have to provide a lot of guarantees to global scale applications.
These 

## Introduction

Modern planet-scale applications increasingly use database to provide users with fast, available and consistent data.
Developpers use these databases with complicated backends to build these applications. 
They expect their data to be consistency while remaining available.
If a network partition or a crash were to occur they except being able to continue their work while disconnect and data to be safely merged and recovered.
<!-- The majority of database use transactions to provide developpers with a simple and reliable way to interact with their data. -->
Journaling, materializing, checkpointing, truncation and recovery are well-known mechanisms that can be used to maintain speed and consistency.
Their interplay is known to be tricky and their implementation if often obscur.
This leads to situation where a developper doesn't know what to expect just based on the different mechanisms used to implement t
We wrote a formal model, came up with some simple design and we show through comparaison that this is true.

Developpers use complicated backend to build their applications. 

## Problem


In this paper we present an approach to designing databases through the study of an existing database.
Our target consistency being Transactionnal Causal Consistency[] (TCC) we chose to study Antidote.

Antidote is geo-distributed across a number of data centers (DCs). Each DC is
strongly consistent; however, updates can originate concurrently at any DC under the Transactional Causal Consistency model.
Antidote persists its updates in a sequential journal of operation. 
To recover from a crash, or materialise a version of interest that is not present in cache, requires to (re-)execute operations from the journal from the beginning.
To speed this up we propose a to add a checkpointing mecanism that ensures data integrity while being able to provide TCC.

This provides two benefits, reduce the overall size of the data, by truncating unnecessary operation after checkpointing and re-execute operation only after the checkpoint.
Journaling, checkpointing and journal truncation are well known mecanisms but their interplay is tricky and hard to do correctly in a database like Antidote because of it's complex geo-distributed and sharded structure.

In this paper we present an approach to design and add features to existing databases through the study of an existing database.
We choose to concentrate on the problem of checkpoint and truncation.

Problem in general what are the invariants we need to write down.
then we generalize to something where there isn't a total order but there's a partial order.

# System model

### Transactions

Transactions are a set operations, reads and writes, that are terminated by an abort or a commit.
Transactions are defined by a set of operations that is terminated by a an or a commit.
Operations are either reads which are read from a Snapshot defined at that start of the transaction or writes that have an effect on said snapshot.
Visibility of these operation are defined by the consistency model used in the database.
In our work we will consider TCC+ as our main consistency target for our database.

### Snapshot


### Object Version - Lookup


## Description d'un model commun
First we talk about the structures of interest in our model.
(Journal, Checkpoint store, Client).
Than we talk about the concepts that are important for correctness.
Once we have the structures and the concept we present the APIs call that link different structures toghether and their invariants. 
We divide our model in two main category:
- Journal based
- State based
### Description d'un model state base

### Description du model operation based
## Reference implementation
We implement multiple prototypes.
Starting with a simple basic implementation that statisfies our model.
Then we add the different structures and concepts of interest in our model one by one show through testing that each model is equivalent to the previous one.
Writing relevant test cases is one area that is important to show that the consistency requirements are upheld in our implementation.

## Conclusion

## Commentaire

Talk about the data type used in the paper.

Do we need to flush the cache after a checkpoint? 
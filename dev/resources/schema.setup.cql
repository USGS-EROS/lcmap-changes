CREATE KEYSPACE IF NOT EXISTS lcmap_changes_dev
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1};


CREATE TABLE IF NOT EXISTS lcmap_changes_dev.schedule(
    x_start int,
    x_end   int,
    y_start int,
    y_end   int,
    algorithm text,
    PRIMARY KEY()
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };


CREATE TABLE IF NOT EXISTS lcmap_changes_dev.changes(
    x int,
    y int,
    algorithm text,
    last_modified timestamp,
    result text,
    result_md5 text,
    result_status text,
    inputs_url text,
    inputs_md5 text,
    PRIMARY KEY ((x, y), algorithm, last_modified)
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };

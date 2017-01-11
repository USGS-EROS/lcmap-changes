CREATE KEYSPACE IF NOT EXISTS lcmap_changes_dev
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS lcmap_changes_dev.changes(
    tile_x int,
    tile_y int,
    algorithm text,
    tile_status text STATIC,
    tile_status_last_updated timestamp STATIC,
    inputs_url text STATIC,
    inputs_md5 text STATIC,
    x int,
    y int,
    result text,
    result_md5 text,
    result_last_updated timestamp,
    result_status text,
    result_status_last_updated timestamp,
    PRIMARY KEY ((tile_x, tile_y, algorithm), x, y)
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };

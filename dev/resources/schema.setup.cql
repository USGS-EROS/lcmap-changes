CREATE KEYSPACE IF NOT EXISTS lcmap_changes_dev
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS lcmap_changes_dev.changes(
    tile_x int,
    tile_y int,
    tile_status,
    tile_status_last_updated,
    algorithm text,
    x int,
    y int,
    result text,
    result_md5 text,
    result_last_updated timestamp,
    result_status text,
    result_status_last_updated timestamp,
    inputs_url text,
    inputs_md5 text,
    PRIMARY KEY ((tile_x, tile_y, algorithm), x, y, result_last_updated)
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };

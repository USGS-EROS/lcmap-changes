CREATE KEYSPACE IF NOT EXISTS lcmap_changes_dev
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS lcmap_changes_dev.algorithms(
    algorithm text PRIMARY KEY,
    inputs_endpoint text,
    enabled boolean
);

CREATE TABLE IF NOT EXISTS lcmap_changes_dev.results(
    tile_x int,
    tile_y int,
    algorithm text,
    x int,
    y int,
    result text,
    result_md5 text,
    result_status text,
    result_produced timestamp,
    tile_update_requested timestamp STATIC,
    tile_update_began timestamp STATIC,
    tile_update_ended timestamp STATIC,
    inputs_url text STATIC,
    inputs_md5 text STATIC,
    PRIMARY KEY ((tile_x, tile_y, algorithm), x, y)
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };
